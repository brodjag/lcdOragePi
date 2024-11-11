package com.unikum.mylcdtest

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.lifecycleScope
import com.brodjag.lcd.BroLcd
import com.unikum.mylcdtest.ui.theme.MyLcdTestTheme
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyLcdTestTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Greeting(
                        name = "Android",
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }


        // runAsRoot()
        lifecycleScope.launch(IO) {
            BroLcd().apply {
                begin()
                while (true) {
                    val tempInt = readTemp()?.toInt() ?: -1
//            (0x41..0x6f).forEach{i->write(i.toUByte())}
                    setCursor(0u, 0u)
                    writeStr("cpu t:${tempInt / 1000} C")
                    delay(100)
                }
            }

        }
    }
}

    @Composable
    fun Greeting(name: String, modifier: Modifier = Modifier) {
        Text(
            text = "Hello $name!",
            modifier = modifier
        )
    }

    @Preview(showBackground = true)
    @Composable
    fun GreetingPreview() {
        MyLcdTestTheme {
            Greeting("Android")
        }
    }