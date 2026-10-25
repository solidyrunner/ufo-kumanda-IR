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
    @Volatile private var isScanning = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        irManager = getSystemService(Context.CONSUMER_IR_SERVICE) as? ConsumerIrManager
        tvStatus = findViewById(R.id.tvStatus)

        if (irManager == null || !irManager!!.hasIrEmitter()) {
            Toast.makeText(this, "IR Donanımı Bulunamadı!", Toast.LENGTH_LONG).show()
        }

        // 1. UFO UK/L Gerçek Power: Adres=0x70, Komut=0xA8
        findViewById<Button>(R.id.btnRealPower).setOnClickListener {
            send4BytePacket(0x70, 0xA8, "UFO Power (Addr: 0x70, Cmd: 0xA8)")
        }

        // 2. Kademe (+): Adres=0x70, Komut=0xA2
        findViewById<Button>(R.id.btnLevelUp).setOnClickListener {
            send4BytePacket(0x70, 0xA2, "UFO Kademe + (Addr: 0x70, Cmd: 0xA2)")
        }

        // 3. Kademe (-): Adres=0x70, Komut=0x90
        findViewById<Button>(R.id.btnLevelDown).setOnClickListener {
            send4BytePacket(0x70, 0x90, "UFO Kademe - (Addr: 0x70, Cmd: 0x90)")
        }

        // 4. 0x70 Adresindeki tüm 256 komutu tara
        findViewById<Button>(R.id.btnScan0x70).setOnClickListener {
            if (!isScanning) {
                isScanning = true
                Thread {
                    for (cmd in 0..255) {
                        if (!isScanning) break
                        runOnUiThread {
                            tvStatus.text = "Adres: 0x70 | Komut: 0x%02X (%d/255)".format(cmd, cmd)
                        }
                        transmitSustained(0x70, cmd, repeats = 4)
                        Thread.sleep(180)
                    }
                    runOnUiThread {
                        if (isScanning) {
                            tvStatus.text = "0x70 taraması tamamlandı!"
                            isScanning = false
                        }
                    }
                }.start()
            }
        }

        // 5. Alternatif Cihaz Adreslerini Tara (0x40, 0x86, 0x01, 0x20)
        findViewById<Button>(R.id.btnScanAddresses).setOnClickListener {
            if (!isScanning) {
                isScanning = true
                val candidateAddresses = intArrayOf(0x40, 0x86, 0x01, 0x20, 0x02, 0x10, 0x77)
                Thread {
                    for (addr in candidateAddresses) {
                        if (!isScanning) break
                        runOnUiThread {
                            tvStatus.text = "Adres Deneniyor: 0x%02X (Power: 0xA8)".format(addr)
                        }
                        transmitSustained(addr, 0xA8, repeats = 5)
                        Thread.sleep(600)
                    }
                    runOnUiThread {
                        if (isScanning) {
                            tvStatus.text = "Adres taraması bitti."
                            isScanning = false
                        }
                    }
                }.start()
            }
        }

        findViewById<Button>(R.id.btnStop).setOnClickListener {
            isScanning = false
            tvStatus.text = "Tarama durduruldu."
        }
    }

    private fun send4BytePacket(address: Int, command: Int, label: String) {
        tvStatus.text = "Gönderiliyor:\n$label"
        Thread {
            // 5 tekrar = parazit filtresini aşmak için ~550ms kesintisiz akış
            transmitSustained(address, command, repeats = 5)
            runOnUiThread {
                tvStatus.text = "Gönderildi:\n$label"
            }
        }.start()
    }

    /**
     * 4 Ayrı Baytı (Address, ~Address, Command, ~Command) oluşturan 
     * ve 38 kHz taşıyıcıyla LSB-First ileten NEC motoru
     */
    private fun transmitSustained(address: Int, command: Int, repeats: Int) {
        val manager = irManager ?: return
        val pattern = ArrayList<Int>()

        // 4 Kod Parçası:
        val b1 = address and 0xFF         // 1. Bayt: Adres
        val b2 = address.inv() and 0xFF   // 2. Bayt: Adresin Tersi
        val b3 = command and 0xFF         // 3. Bayt: Komut
        val b4 = command.inv() and 0xFF   // 4. Bayt: Komutun Tersi

        val packet = intArrayOf(b1, b2, b3, b4)

        for (r in 0 until repeats) {
            // NEC Leader (9ms HIGH, 4.5ms LOW)
            pattern.add(9000
