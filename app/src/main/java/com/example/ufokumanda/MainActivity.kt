package com.example.ufokumanda

import android.content.Context
import android.hardware.ConsumerIrManager
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private var irManager: ConsumerIrManager? = null
    private lateinit var tvCurrentCode: TextView
    private lateinit var tvDetails: TextView

    @Volatile
    private var isScanning = false
    private var lastUsedProtocol = "NEC_LSB"
    private var lastUsedAddress = 0x00
    private var lastUsedCommand = 0xA8

    // Türkiye ve OEM ısıtıcılarında kullanılan bilinen (Adres, Komut, Protokol) havuzu
    private val profileList = ArrayList<Triple<Int, Int, String>>().apply {
        // En yaygın UFO UK/L ve türevleri (NEC LSB)
        val necCommands = intArrayOf(
            0xA8, 0x80, 0x12, 0x00, 0x01, 0x02, 0x10, 0x20, 0xA2, 0x90, 
            0x48, 0x68, 0xC2, 0x18, 0x28, 0x38, 0x08, 0x04, 0x0A, 0x14,
            0x15, 0x45, 0x09, 0x02, 0xFD, 0xDF, 0xEF, 0x57, 0x7F, 0xFF
        )
        for (cmd in necCommands) {
            add(Triple(0x00, cmd, "NEC_LSB")) // Address: 0x00
            add(Triple(0x00, cmd, "NEC_MSB")) // Address: 0x00 (MSB varyasyonu)
        }
        // Extended Address varyasyonları (Alf, Kumtel, Luxell)
        val extAddresses = intArrayOf(0x40, 0x70, 0x86, 0x01, 0x02, 0x20, 0x80)
        for (addr in extAddresses) {
            add(Triple(addr, 0xA8, "NEC_LSB"))
            add(Triple(addr, 0x80, "NEC_LSB"))
            add(Triple(addr, 0x00, "NEC_LSB"))
        }
        // TC9012 (Toshiba formatı: 4.5ms / 4.5ms) varyasyonları
        for (cmd in intArrayOf(0xA8, 0x80, 0x12, 0x00, 0x01, 0xA2, 0x90)) {
            add(Triple(0x00, cmd, "TC9012"))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        irManager = getSystemService(Context.CONSUMER_IR_SERVICE) as? ConsumerIrManager
        tvCurrentCode = findViewById(R.id.tvCurrentCode)
        tvDetails = findViewById(R.id.tvDetails)

        if (irManager == null || !irManager!!.hasIrEmitter()) {
            Toast.makeText(this, "IR Donanımı Bulunamadı!", Toast.LENGTH_LONG).show()
        }

        // 1. Profil Taraması
        findViewById<Button>(R.id.btnScanProfiles).setOnClickListener {
            if (!isScanning) {
                isScanning = true
                runProfileScan()
            }
        }

        // 2. Brute-Force Taraması (0x00 -> 0xFF)
        findViewById<Button>(R.id.btnBruteForce).setOnClickListener {
            if (!isScanning) {
                isScanning = true
                runBruteForceScan()
            }
        }

        // Durdur / Kodu Yakala
        findViewById<Button>(R.id.btnStop).setOnClickListener {
            isScanning = false
            tvDetails.text = "Tarama DURDURULDU. Son sinyal kilitlendi."
        }

        // 1 Geri
        findViewById<Button>(R.id.btnPrev).setOnClickListener {
            lastUsedCommand = (lastUsedCommand - 1) and 0xFF
            resendCurrent()
        }

        // Tekrar Gönder
        findViewById<Button>(R.id.btnResend).setOnClickListener {
            resendCurrent()
        }

        // 1 İleri
        findViewById<Button>(R.id.btnNext).setOnClickListener {
            lastUsedCommand = (lastUsedCommand + 1) and 0xFF
            resendCurrent()
        }
    }

    private fun resendCurrent() {
        sendBurstSignal(lastUsedAddress, lastUsedCommand, lastUsedProtocol)
        tvCurrentCode.text = "Gönderildi: Addr 0x%02X, Cmd 0x%02X (%s)".format(lastUsedAddress, lastUsedCommand, lastUsedProtocol)
    }

    private fun runProfileScan() {
        Thread {
            for ((index, item) in profileList.withIndex()) {
                if (!isScanning) break
                lastUsedAddress = item.first
                lastUsedCommand = item.second
                lastUsedProtocol = item.third

                runOnUiThread {
                    tvCurrentCode.text = "[%d/%d] Addr: 0x%02X | Cmd: 0x%02X".format(index + 1, profileList.size, lastUsedAddress, lastUsedCommand)
                    tvDetails.text = "Protokol: $lastUsedProtocol (Burst x2)"
                }

                sendBurstSignal(lastUsedAddress, lastUsedCommand, lastUsedProtocol)
                Thread.sleep(220) // MCU tepki süresi
            }
            runOnUiThread {
                if (isScanning) {
                    tvCurrentCode.text = "Profil taraması bitti!"
                    isScanning = false
                }
            }
        }.start()
    }

    private fun runBruteForceScan() {
        Thread {
            for (cmd in 0..255) {
                if (!isScanning) break
                lastUsedAddress = 0x00
                lastUsedCommand = cmd
                lastUsedProtocol = "NEC_LSB"

                runOnUiThread {
                    tvCurrentCode.text = "Brute-Force: Cmd 0x%02X (%d/255)".format(cmd, cmd)
                    tvDetails.text = "Adres: 0x00 | Çift Paket Gönderiliyor..."
                }

                sendBurstSignal(0x00, cmd, "NEC_LSB")
                Thread.sleep(180)
            }
            runOnUiThread {
                if (isScanning) {
                    tvCurrentCode.text = "Brute-force tamamlandı!"
                    isScanning = false
                }
            }
        }.start()
    }

    /**
     * MCU filtrelerini aşmak için 2 TAM PAKETİ (Burst) arka arkaya basan motor.
     */
    private fun sendBurstSignal(address: Int, command: Int, protocol: String) {
        val manager = irManager ?: return
        val pattern = ArrayList<Int>()

        val invAddress = address.inv() and 0xFF
        val invCommand = command.inv() and 0xFF
        val bytes = intArrayOf(address, invAddress, command, invCommand)

        // Arka arkaya 2 tam paket (Burst)
        for (repeat in 0..1) {
            when (protocol) {
                "TC9012" -> {
                    // Toshiba formatı: 4.5ms Header
                    pattern.add(4500)
                    pattern.add(4500)
                }
                else -> {
                    // Standart NEC: 9ms Header
                    pattern.add(9000)
                    pattern.add(4500)
                }
            }

            for (b in bytes) {
                for (bitIdx in 0..7) {
                    val bit = if (protocol == "NEC_MSB") {
                        (b shr (7 - bitIdx)) and 1
                    } else {
                        (b shr bitIdx) and 1 // LSB-first
                    }

                    pattern.add(560)
                    pattern.add(if (bit == 1) 1690 else 560)
                }
            }

            // Stop bit
            pattern.add(560)

            // Paketler arası dinleme boşluğu (35 ms)
            if (repeat == 0) {
                pattern.add(35000)
            }
        }

        try {
            manager.transmit(38000, pattern.toIntArray())
        } catch (_: Exception) {}
    }
}
