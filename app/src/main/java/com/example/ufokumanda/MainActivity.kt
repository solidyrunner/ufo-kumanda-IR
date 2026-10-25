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

    // UFO UK/L ve muadil tüm Türkiye ısıtıcı kodları (Ham 32-bit HEX)
    private val rawCodes = listOf(
        Pair("1. UFO UK/L Orijinal", 0x00FFA857L),
        Pair("2. UFO UK Alternatif", 0x00FF807FL),
        Pair("3. UFO Extended NEC",  0x708FA857L),
        Pair("4. UFO Kademe (+)",    0x00FFA25DL),
        Pair("5. Kumtel/Alf Power",  0x00FF02FDL),
        Pair("6. Alf Extended",      0x40BFA857L),
        Pair("7. UFO Star/Line",     0x00FF10EFL)
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        irManager = getSystemService(Context.CONSUMER_IR_SERVICE) as? ConsumerIrManager
        tvStatus = findViewById(R.id.tvStatus)

        findViewById<Button>(R.id.btnPower).setOnClickListener {
            sendSingleNec(0x00FFA857L)
            tvStatus.text = "Orijinal UFO basıldı (0x00FFA857)"
        }

        findViewById<Button>(R.id.btnLevelUp).setOnClickListener {
            sendSingleNec(0x00FFA25DL)
            tvStatus.text = "Kademe (+) basıldı (0x00FFA25D)"
        }

        findViewById<Button>(R.id.btnForceWake).setOnClickListener {
            // Sadece tek tıkla en olası 3 kodu arka arkaya değil, 300ms arayla temiz ilet
            Thread {
                runOnUiThread { tvStatus.text = "Temiz uyandırma sinyalleri atılıyor..." }
                sendSingleNec(0x00FFA857L)
                Thread.sleep(300)
                sendSingleNec(0x00FFA25DL)
                Thread.sleep(300)
                sendSingleNec(0x00FF807FL)
                runOnUiThread { tvStatus.text = "Tamamlandı." }
            }.start()
        }

        findViewById<Button>(R.id.btnScanAll).setOnClickListener {
            Thread {
                for ((index, item) in rawCodes.withIndex()) {
                    runOnUiThread {
                        tvStatus.text = "Deneniyor [${index + 1}/${rawCodes.size}]:\n${item.first}"
                    }
                    sendSingleNec(item.second)
                    Thread.sleep(1000)
                }
                runOnUiThread { tvStatus.text = "Tarama bitti." }
            }.start()
        }
    }

    /**
     * Xiaomi HAL katmanının en kararlı çalıştığı SAF TEKİL NEC paketi
     */
    private fun sendSingleNec(hexCode: Long) {
        val manager = irManager ?: return
        val pattern = ArrayList<Int>()

        // Leader: 9ms Mark, 4.5ms Space
        pattern.add(9000)
        pattern.add(4500)

        // 32-bit Veri (MSB to LSB standard bit stream)
        for (i in 31 downTo 0) {
            val bit = (hexCode shr i) and 1L
            pattern.add(560)
            pattern.add(if (bit == 1L) 1690 else 560)
        }

        // Stop bit
        pattern.add(560)

        try {
            manager.transmit(38000, pattern.toIntArray())
        } catch (e: Exception) {
            runOnUiThread {
                Toast.makeText(this@MainActivity, "Hata: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
