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
    private lateinit var tvProgress: TextView

    @Volatile private var isScanning = false
    private var currentCmd = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        irManager = getSystemService(Context.CONSUMER_IR_SERVICE) as? ConsumerIrManager
        tvStatus = findViewById(R.id.tvStatus)
        tvProgress = findViewById(R.id.tvProgress)

        if (irManager == null || !irManager!!.hasIrEmitter()) {
            Toast.makeText(this, "IR Donanımı Bulunamadı!", Toast.LENGTH_LONG).show()
        }

        // 256 Kodluk Tam Taramayı Başlat
        findViewById<Button>(R.id.btnFullScan).setOnClickListener {
            if (!isScanning) {
                isScanning = true
                runFullSustainedScan()
            }
        }

        // Durdur
        findViewById<Button>(R.id.btnStop).setOnClickListener {
            isScanning = false
            tvProgress.text = "TARAMA DURDURULDU! Son kod: 0x%02X".format(currentCmd)
        }

        // 1 Geri
        findViewById<Button>(R.id.btnPrev).setOnClickListener {
            currentCmd = (currentCmd - 1) and 0xFF
            sendAndDisplay(currentCmd)
        }

        // Tekrar Gönder
        findViewById<Button>(R.id.btnResend).setOnClickListener {
            sendAndDisplay(currentCmd)
        }

        // 1 İleri
        findViewById<Button>(R.id.btnNext).setOnClickListener {
            currentCmd = (currentCmd + 1) and 0xFF
            sendAndDisplay(currentCmd)
        }

        // Hızlı Butonlar
        findViewById<Button>(R.id.btnQuickA8).setOnClickListener { sendAndDisplay(0xA8) }
        findViewById<Button>(R.id.btnQuickA2).setOnClickListener { sendAndDisplay(0xA2) }
        findViewById<Button>(R.id.btnQuick80).setOnClickListener { sendAndDisplay(0x80) }
    }

    private fun sendAndDisplay(cmd: Int) {
        currentCmd = cmd
        tvStatus.text = "Kod Gönderiliyor: 0x%02X (%d/255)".format(cmd, cmd)
        Thread {
            transmitSustainedNec(0x00, cmd)
            runOnUiThread {
                tvProgress.text = "Son Gönderilen: 0x%02X".format(cmd)
            }
        }.start()
    }

    /**
     * 0'dan 255'e kadar her kodu 450ms boyunca kesintisiz basarak tarar.
     */
    private fun runFullSustainedScan() {
        Thread {
            for (cmd in 0..255) {
                if (!isScanning) break
                currentCmd = cmd

                runOnUiThread {
                    tvStatus.text = "Taranıyor: Cmd 0x%02X (%d/255)".format(cmd, cmd)
                    tvProgress.text = "İlerleme: %%%.1f".format((cmd / 255.0) * 100)
                }

                // 4 tekrar = ~440ms boyunca aralıksız sinyal (filtreyi deler)
                transmitSustainedNec(0x00, cmd)
                Thread.sleep(160) // Kodlar arası kısa bekleme
            }

            runOnUiThread {
                if (isScanning) {
                    tvStatus.text = "256 kodun tamamı tarandı!"
                    tvProgress.text = "Bip sesi duyuldu mu?"
                    isScanning = false
                }
            }
        }.start()
    }

    /**
     * NEC Standart Çerçevesi (4 Tekrarlı Kesintisiz Akış ~450ms)
     */
    private fun transmitSustainedNec(address: Int, command: Int) {
        val manager = irManager ?: return
        val pattern = ArrayList<Int>()

        val invAddress = address.inv() and 0xFF
        val invCommand = command.inv() and 0xFF
        val bytes = intArrayOf(address, invAddress, command, invCommand)

        // 4 tam paket arka arkaya (parazit önleyici MCU filtresini aşar)
        for (r in 0..3) {
            // Leader
            pattern.add(9000)
            pattern.add(4500)

            // 32-bit LSB-First Veri
            for (b in bytes) {
                for (bitIdx in 0..7) {
                    val bit = (b shr bitIdx) and 1
                    pattern.add(560)
                    pattern.add(if (bit == 1) 1690 else 560)
                }
            }

            // Stop bit
            pattern.add(560)

            // Paketler arası bekleme boşluğu (40ms)
            if (r < 3) {
                pattern.add(40000)
            }
        }

        try {
            manager.transmit(38000, pattern.toIntArray())
        } catch (_: Exception) {}
    }
}
