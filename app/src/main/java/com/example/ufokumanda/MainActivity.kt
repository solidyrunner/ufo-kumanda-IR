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

    // UFO UK serilerinde kullanılan aday kodlar (Address, Command)
    private val candidateCodes = listOf(
        Pair(0x00, 0x80), // 1. En yaygın UFO Standart Power
        Pair(0x00, 0x12), // 2. UFO Alternatif Power
        Pair(0x00, 0x00), // 3. Micatronic Power
        Pair(0x01, 0x01), // 4. UK-L Power
        Pair(0x40, 0x80), // 5. Alf/Line Power
        Pair(0x20, 0x10)  // 6. Ekstra varyasyon
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        irManager = getSystemService(Context.CONSUMER_IR_SERVICE) as? ConsumerIrManager
        tvStatus = findViewById(R.id.tvStatus)

        if (irManager == null || !irManager!!.hasIrEmitter()) {
            Toast.makeText(this, "IR Verici donanımı bulunamadı!", Toast.LENGTH_LONG).show()
        }

        findViewById<Button>(R.id.btnCode1).setOnClickListener { testCode(0) }
        findViewById<Button>(R.id.btnCode2).setOnClickListener { testCode(1) }
        findViewById<Button>(R.id.btnCode3).setOnClickListener { testCode(2) }
        findViewById<Button>(R.id.btnCode4).setOnClickListener { testCode(3) }
        findViewById<Button>(R.id.btnCode5).setOnClickListener { testCode(4) }

        findViewById<Button>(R.id.btnAutoScan).setOnClickListener {
            runAutoScan()
        }
    }

    private fun testCode(index: Int) {
        val (addr, cmd) = candidateCodes[index]
        tvStatus.text = "Kod ${index + 1} yollandı (Addr: 0x%02X, Cmd: 0x%02X)".format(addr, cmd)
        sendNecWithRepeat(addr, cmd)
    }

    private fun runAutoScan() {
        Thread {
            for (i in candidateCodes.indices) {
                val (addr, cmd) = candidateCodes[i]
                runOnUiThread {
                    tvStatus.text = "Deneniyor (${i + 1}/${candidateCodes.size}): Addr: 0x%02X Cmd: 0x%02X".format(addr, cmd)
                }
                sendNecWithRepeat(addr, cmd)
                Thread.sleep(1200) // Cihazın tepki vermesi için 1.2 saniye bekle
            }
            runOnUiThread {
                tvStatus.text = "Tarama bitti! Bip sesi geldi mi?"
            }
        }.start()
    }

    /**
     * Sinyali tek bir darbe dizisi içinde 2 KEZ TEKRARLAR (Burst/Repeat).
     * Böylece cihazın parazit önleyici AGC filtresine takılmaz ve Android HAL çakışması yaşanmaz.
     */
    private fun sendNecWithRepeat(address: Int, command: Int) {
        val manager = irManager ?: return
        val pattern = ArrayList<Int>()

        val invAddress = address.inv() and 0xFF
        val invCommand = command.inv() and 0xFF
        val bytes = intArrayOf(address, invAddress, command, invCommand)

        // Sinyali arka arkaya 2 defa gönder (Araya 40ms boşluk koyarak)
        for (repeat in 0..1) {
            // Leader Code: 9ms High, 4.5ms Low
            pattern.add(9000)
            pattern.add(4500)

            // 32-bit Veri (LSB-First)
            for (b in bytes) {
                for (bitIdx in 0..7) {
                    pattern.add(560)
                    if (((b shr bitIdx) and 1) == 1) {
                        pattern.add(1690) // Logic 1
                    } else {
                        pattern.add(560)  // Logic 0
                    }
                }
            }

            // Stop Bit
            pattern.add(560)

            // Paketler arası dinleme boşluğu (40 ms)
            if (repeat == 0) {
                pattern.add(40000)
            }
        }

        try {
            manager.transmit(38000, pattern.toIntArray())
        } catch (e: Exception) {
            Toast.makeText(this, "Hata: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
