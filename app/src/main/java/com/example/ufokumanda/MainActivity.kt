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
            Toast.makeText(this, "Bu telefonda kızılötesi (IR) verici bulunamadı!", Toast.LENGTH_LONG).show()
        }

        val btnPower = findViewById<Button>(R.id.btnPower)
        val btnUp = findViewById<Button>(R.id.btnUp)
        val btnDown = findViewById<Button>(R.id.btnDown)

        btnPower.setOnClickListener {
            sendNecSignal(0x00FF807FL)
            Toast.makeText(this, "Açma/Kapama Sinyali yollandı", Toast.LENGTH_SHORT).show()
        }

        btnUp.setOnClickListener {
            sendNecSignal(0x00FF20DFL)
        }

        btnDown.setOnClickListener {
            sendNecSignal(0x00FFA05FL)
        }
    }

    private fun sendNecSignal(hexCode: Long) {
        val manager = irManager ?: return
        val pattern = ArrayList<Int>()

        // 1. NEC Leader Code
        pattern.add(9000)
        pattern.add(4500)

        // 2. 32-bit Data
        for (i in 31 downTo 0) {
            val bit = (hexCode shr i) and 1L
            pattern.add(560)
            if (bit == 1L) {
                pattern.add(1690)
            } else {
                pattern.add(560)
            }
        }

        // 3. Stop Bit
        pattern.add(560)

        try {
            manager.transmit(38000, pattern.toIntArray())
        } catch (e: Exception) {
            Toast.makeText(this, "Hata: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
