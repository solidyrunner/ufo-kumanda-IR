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
    private lateinit var tvStatus: TextView
    private var isScanning = false

    // Veritabanlarındaki tüm bilinen UFO, Alf ve Türkiye rezistans kumanda kodları
    private val necCodes = listOf(
        Pair("LIRC UFO Power", 0x00FFA857L),
        Pair("UFO Standart 1", 0x00FF807FL),
        Pair("UFO UK/Micatronic", 0x00FF02FDL),
        Pair("UFO Star Power", 0x00FF10EFL),
        Pair("UFO Line Power", 0x00FFC23DL),
        Pair("UFO UK Alternatif", 0x00FF12EDL),
        Pair("UFO Toggle Standart", 0x00FF00FFL),
        Pair("Alf/Line Hybr.", 0x40BFA857L),
        Pair("Alf Power", 0x40BF807FL),
        Pair("UFO V4 Power", 0x00FF48B7L),
        Pair("UFO V5 Power", 0x00FF6897L)
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        irManager = getSystemService(Context.CONSUMER_IR_SERVICE) as? ConsumerIrManager
        tvStatus = findViewById(R.id.tvStatus)

        if (irManager == null || !irManager!!.hasIrEmitter()) {
            Toast.makeText(this, "IR Donanımı Bulunamadı!", Toast.LENGTH_LONG).show()
        }

        // 1. Asıl Aday: LIRC UFO Power (0xA857)
        findViewById<Button>(R.id.btnMainCandidate).setOnClickListener {
            sendNec38k(0x00FFA857L)
            tvStatus.text = "0x00FFA857 yollandı (Orijinal UFO)"
        }

        // Alternatif hızlı butonlar
        findViewById<Button>(R.id.btnAlt1).setOnClickListener {
            sendNec38k(0x00FF02FDL)
            tvStatus.text = "0x00FF02FD yollandı"
        }

        findViewById<Button>(R.id.btnAlt2).setOnClickListener {
            sendNec38k(0x00FF10EFL)
            tvStatus.text = "0x00FF10EF yollandı"
        }

        findViewById<Button>(R.id.btnAlt3).setOnClickListener {
            sendRc5(0, 12) // RC5 Address 0, Command 12 (Power)
            tvStatus.text = "RC-5 (36 kHz) Sinyali yollandı"
        }

        // Otomatik Tarama Başlat
        findViewById<Button>(R.id.btnAutoScan).setOnClickListener {
            if (!isScanning) {
                isScanning = true
                runFullScan()
            }
        }

        // Taramayı Durdur
        findViewById<Button>(R.id.btnStopScan).setOnClickListener {
            isScanning = false
            tvStatus.text = "Tarama kullanıcı tarafından durduruldu."
        }
    }

    private fun runFullScan() {
        Thread {
            // Önce tüm NEC kodlarını dene
            for ((index, item) in necCodes.withIndex()) {
                if (!isScanning) break
                runOnUiThread {
                    tvStatus.text = "Deneniyor [${index + 1}/${necCodes.size}]:\n${item.first} (0x%08X)".format(item.second)
                }
                sendNec38k(item.second)
                Thread.sleep(1200)
            }

            // Ardından RC-5 varyasyonlarını dene
            val rc5Addresses = intArrayOf(0, 5, 12, 20)
            for (addr in rc5Addresses) {
                if (!isScanning) break
                runOnUiThread {
                    tvStatus.text = "Deneniyor: RC-5 (36kHz) Addr: $addr Cmd: 12"
                }
                sendRc5(addr, 12)
                Thread.sleep(1200)
            }

            runOnUiThread {
                if (isScanning) {
                    tvStatus.text = "Tüm kodlar tarandı! Bip sesi duyuldu mu?"
                    isScanning = false
                }
            }
        }.start()
    }

    /**
     * Standart 38 kHz NEC Sinyali (MSB-First + 3 Tekrarlı Burst)
     */
    private fun sendNec38k(hexCode: Long) {
        val manager = irManager ?: return
        val pattern = ArrayList<Int>()

        // 3 Kez tekrarla (filtreye takılmaması için)
        for (repeat in 0..2) {
            pattern.add(9000)
            pattern.add(4500)

            for (i in 31 downTo 0) {
                val bit = (hexCode shr i) and 1L
                pattern.add(560)
                pattern.add(if (bit == 1L) 1690 else 560)
            }

            pattern.add(560)
            pattern.add(40000) // Boşluk
        }

        try {
            manager.transmit(38000, pattern.toIntArray())
        } catch (e: Exception) {
            // Hata logu
        }
    }

    /**
     * 36 kHz Philips RC-5 Protokolü (Manchester Kodlaması)
     */
    private fun sendRc5(address: Int, command: Int) {
        val manager = irManager ?: return
        val pattern = ArrayList<Int>()

        // 14 Bit: 2 Start biti (1, 1), 1 Toggle (0), 5 bit Adres, 6 bit Komut
        val data = (1 shl 13) or (1 shl 12) or (0 shl 11) or ((address and 0x1F) shl 6) or (command and 0x3F)

        for (repeat in 0..2) {
            for (i in 13 downTo 0) {
                val bit = (data shr i) and 1
                if (bit == 1) {
                    // Logic 1: Space (889us), Mark (889us)
                    pattern.add(889)
                    pattern.add(889)
                } else {
                    // Logic 0: Mark (889us), Space (889us)
                    pattern.add(889)
                    pattern.add(889)
                }
            }
            pattern.add(889)
            pattern.add(40000)
        }

        try {
            manager.transmit(36000, pattern.toIntArray())
        } catch (e: Exception) {
            // Hata logu
        }
    }
}
