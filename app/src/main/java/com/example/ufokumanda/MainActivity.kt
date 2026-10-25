package com.example.ufokumanda

import android.content.Context
import android.hardware.ConsumerIrManager
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private var irManager: ConsumerIrManager? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        irManager = getSystemService(Context.CONSUMER_IR_SERVICE) as? ConsumerIrManager

        if (irManager == null || !irManager!!.hasIrEmitter()) {
            Toast.makeText(this, "IR Verici bulunamadı!", Toast.LENGTH_LONG).show()
        }

        val btnPower = findViewById<Button>(R.id.btnPower)
        val btnUp = findViewById<Button>(R.id.btnUp)
        val btnDown = findViewById<Button>(R.id.btnDown)

        // UFO UK serilerinde kullanılan yaygın Power komutları
        btnPower.setOnClickListener {
            // UFO V1 Power (Address: 0x00, Command: 0x80)
            sendNecCommand(0x00, 0x80)
            // UFO V2 Power (Address: 0x00, Command: 0x00)
            sendNecCommand(0x00, 0x00)
            // UFO V3 Power (Address: 0x40, Command: 0x80)
            sendNecCommand(0x40, 0x80)
            Toast.makeText(this, "Aç/Kapat sinyalleri gönderildi", Toast.LENGTH_SHORT).show()
        }

        btnUp.setOnClickListener {
            sendNecCommand(0x00, 0x20)
        }

        btnDown.setOnClickListener {
            sendNecCommand(0x00, 0xA0)
        }
    }

    /**
     * Standart NEC formatı (LSB-First):
     * 9ms Header, 4.5ms Space
     * 8-bit Address, 8-bit Inverted Address
     * 8-bit Command, 8-bit Inverted Command
     * 560us Stop bit
     */
    private fun sendNecCommand(address: Int, command: Int) {
        val manager = irManager ?: return
        val pattern = ArrayList<Int>()

        // 1. Leader Code
        pattern.add(9000)
        pattern.add(4500)

        val invAddress = address.inv() and 0xFF
        val invCommand = command.inv() and 0xFF

        val bytes = intArrayOf(address, invAddress, command, invCommand)

        // 2. 4 Baytı LSB-First (sağdan sola) gönder
        for (b in bytes) {
            for (bitIdx in 0..7) {
                pattern.add(560)
                if (((b shr bitIdx) and 1) == 1) {
                    pattern.add(1690) // Mantık 1
                } else {
                    pattern.add(560)  // Mantık 0
                }
            }
        }

        // 3. Stop Bit
        pattern.add(560)

        try {
            manager.transmit(38000, pattern.toIntArray())
        } catch (e: Exception) {
            Toast.makeText(this, "İletim hatası: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
