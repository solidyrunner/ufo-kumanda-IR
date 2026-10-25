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

    // UFO UK/L için tüm potansiyel (Adres, Komut) çiftleri
    private val ufoCandidates = listOf(
        Pair("UFO UK/L Standart Power", Pair(0x00, 0xA8)),
        Pair("UFO UK/L Kademe (+)",     Pair(0x00, 0xA2)),
        Pair("UFO UK/L Kademe (-)",     Pair(0x00, 0x90)),
        Pair("UFO Rev2 Power",          Pair(0x00, 0x12)),
        Pair("UFO Rev3 Power",          Pair(0x00, 0x80)),
        Pair("UFO Rev4 Power",          Pair(0x00, 0x01)),
        Pair("UFO 40BF Kart Power",     Pair(0x40, 0xA8)),
        Pair("UFO Ters Sinyal Power",   Pair(0x00, 0x15))
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        irManager = getSystemService(Context.CONSUMER_IR_SERVICE) as? ConsumerIrManager
        tvStatus = findViewById(R.id.tvStatus)

        // 1. Asıl UFO Power Tuşu
        findViewById<Button>(R.id.btnPower).setOnClickListener {
            sendNecCommandLsb(0x00, 0xA8)
            tvStatus.text = "Power Komutu yollandı (Addr: 0x00, Cmd: 0xA8)"
        }

        // 2. Kademe Artır (H0 -> H1 -> ... H5)
        findViewById<Button>(R.id.btnLevelUp).setOnClickListener {
            sendNecCommandLsb(0x00, 0xA2)
            tvStatus.text = "Kademe (+) yollandı (Addr: 0x00, Cmd: 0xA2)"
        }

        // 3. Kademe Azalt
        findViewById<Button>(R.id.btnLevelDown).setOnClickListener {
            sendNecCommandLsb(0x00, 0x90)
            tvStatus.text = "Kademe (-) yollandı (Addr: 0x00, Cmd: 0x90)"
        }

        // 4. Zorla Uyandır: Önce Power, 100ms sonra Kademe (+) basar
        findViewById<Button>(R.id.btnForceWake).setOnClickListener {
            Thread {
                runOnUiThread { tvStatus.text = "Uyandırma paketi gönderiliyor..." }
                sendNecCommandLsb(0x00, 0xA8)
                Thread.sleep(150)
                sendNecCommandLsb(0x00, 0xA2)
                runOnUiThread { tvStatus.text = "Uyandırma tamamlandı (H0 -> H1)" }
            }.start()
        }

        // 5. Otomatik Tarama
        findViewById<Button>(R.id.btnScanAll).setOnClickListener {
            Thread {
                for ((index, item) in ufoCandidates.withIndex()) {
                    runOnUiThread {
                        tvStatus.text = "Deneniyor [${index + 1}/${ufoCandidates.size}]:\n${item.first}"
                    }
                    sendNecCommandLsb(item.second.first, item.second.second)
                    Thread.sleep(1200)
                }
                runOnUiThread { tvStatus.text = "Tarama bitti! H modunda değişim oldu mu?" }
            }.start()
        }
    }

    /**
     * Doğru NEC Protokolü İletimi:
     * - 38 kHz Taşıyıcı Frekansı
     * - 9000µs Leader Mark, 4500µs Space
     * - 4 Baytın tamamı LSB-FIRST (Önce Bit 0, sonra Bit 7) basılır
     * - Çift tekrar (Burst) ile cihaz parazit filtresi aşılır
     */
    private fun sendNecCommandLsb(address: Int, command: Int) {
        val manager = irManager ?: return
        val pattern = ArrayList<Int>()

        val invAddress = address.inv() and 0xFF
        val invCommand = command.inv() and 0xFF
        val bytes = intArrayOf(address, invAddress, command, invCommand)

        // Sinyali 2 kez arka arkaya ilet (Burst)
        for (repeat in 0..1) {
            // Header
            pattern.add(9000)
            pattern.add(4500)

            // Her bayt için LSB-first bit iletimi
            for (b in bytes) {
                for (bitIdx in 0..7) {
                    val bit = (b shr bitIdx) and 1
                    pattern.add(560)
                    pattern.add(if (bit == 1) 1690 else 560)
                }
            }

            // Stop bit
            pattern.add(560)

            // Paketler arası dinleme boşluğu (40 ms)
            if (repeat == 0) {
                pattern.add(40000)
            }
        }

        try {
            manager.transmit(38000, pattern.toIntArray())
        } catch (e: Exception) {
            runOnUiThread {
                Toast.makeText(this@MainActivity, "Hata: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
