package com.brodjag.lcd

import android.util.Log
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader


class BroLcd(
    /** Индификатор устройства в I2c шине*/
    var i2cAdress: Byte = 0x3f,
    /** номер шины i2c  **/
    val i2cNumber: Byte = 0,
    /** Подсветка дисплея */
    val isBacklight: Boolean = true


) {
    private var  _numlines:Int=2
    private var _displayfunction:UByte=0u
    private var _displaycontrol:UByte=0u
    private var _displaymode:UByte=0u

    companion object {
        // commands
        const val LCD_CLEARDISPLAY = 0x01u
        const val LCD_RETURNHOME = 0x02u
        const val LCD_ENTRYMODESET = 0x04u
        const val LCD_DISPLAYCONTROL = 0x08u
        const val LCD_CURSORSHIFT = 0x10u
        const val LCD_FUNCTIONSET = 0x20u
        const val LCD_SETCGRAMADDR = 0x40u
        const val LCD_SETDDRAMADDR = 0x80u
        // flags for function set
        const val LCD_8BITMODE =0x10u
        const val LCD_4BITMODE =0x00u
        const val LCD_2LINE =0x08u
        const val LCD_1LINE =0x00u
        const val LCD_5x10DOTS =0x04u
        const val LCD_5x8DOTS =0x00u

        // flags for display on/off control
        const val LCD_DISPLAYON= 0x04u
        const val LCD_DISPLAYOFF =0x00u
        const val LCD_CURSORON =0x02u
        const val LCD_CURSOROFF =0x00u
        const val LCD_BLINKON =0x01u
        const val LCD_BLINKOFF =0x00u

        // flags for display entry mode
        const val LCD_ENTRYRIGHT= 0x00u
        const val LCD_ENTRYLEFT =0x02u
        const val LCD_ENTRYSHIFTINCREMENT= 0x01u
        const val LCD_ENTRYSHIFTDECREMENT= 0x00u
    }


    private fun writeI2c(byteIn: UByte) {
        try {
            val proc = Runtime.getRuntime()
                // .exec(" i2cdetect -y 0") //Whatever you want to execute
                .exec("i2cset -y ${i2cNumber} ${i2cAdress} ${byteIn}")
            Log.d("BroLcd", "i2cset -y ${i2cNumber} ${i2cAdress} ${byteIn}")
            val read = BufferedReader(
                InputStreamReader(proc.inputStream)
            )
            try {
                proc.waitFor()
            } catch (e: InterruptedException) {
                Log.d("BroLcd", e.message ?: "")
            }
            while (read.ready()) {
                Log.d("BroLcd", read.readLine() ?: "")
            }
        } catch (e: IOException) {
            Log.d("BroLcd", e.message ?: "")
        }
    }

    /** выполнение команды в терминалде и чтение одной строки
     * @param pramCommand команда для выполнения в терминале  */
    private fun runTermCommand(pramCommand:String):String?{
        try {
            val proc = Runtime.getRuntime()
                .exec(pramCommand)

            val read = BufferedReader(
                InputStreamReader(proc.inputStream)
            )
            try {
                proc.waitFor()
                Log.d("runTermCommand", "waitFor finish")
            } catch (e: InterruptedException) {
                Log.d("runTermCommand", "error ${e.message} ")
            }
            var resultText=""
            while (read.ready()) {
                resultText=resultText+read.readLine()
                Log.d("runTermCommand", "${resultText}")
              ///  return lineTxt
            }
            Log.d("runTermCommand", "answer: $resultText")
            return resultText

        } catch (e: IOException) {
            Log.d("runTermCommand", e.message ?: "")
            return e.message
        }
        return null
    }

    /** Чтение температуры процессора x100 C */
     fun readTemp():String? {
        return   runTermCommand("cat /sys/devices/virtual/thermal/thermal_zone0/temp")
    }


    suspend fun pulseEnable(_data: UByte) {
        expanderWrite(_data or 0b0000_0100u) // En high
        delay(1) // enable pulse must be >450ns
        expanderWrite(_data and 0b0000_0100u.inv().toUByte()) // En low
        delay(50) // commands need > 37us to settle
    }


    private fun expanderWrite(_data: UByte) {
        if (isBacklight) writeI2c(_data or 0b0000_1000u)
        else writeI2c(_data)
    }

    private suspend fun write4bits(value: UByte) {
       // expanderWrite(value)  //мож удалить эту лишнюю чтрочку
        pulseEnable(value)
    }

    /**Выполнение команды или записи **/
    private suspend fun send(value: UByte, mode: UByte) {
        val highnib: UByte = value and 0xf0u
        val lownib: UByte = ((value.toInt() shl 4) and 0xf0).toUByte()
        Log.d("BroLcd","h="+highnib)
        Log.d("BroLcd","l="+lownib)
        write4bits((highnib) or mode)
        write4bits((lownib) or mode)
    }

    /** Запись байта в память */
    suspend fun write(value: UByte) {
        send(value, 0b0000_0001u);
    }

    suspend fun writeStr(str:String) {
        str.onEach { char->
            write((char.code.toUInt() and 0xffu).toUByte())
        }

    }

    /** Выполнение команды */
    suspend fun command(value: UByte) {
        send(value, 0u)
    }

    /********** high level commands, for the user! */
    suspend fun clear() {
        command(LCD_CLEARDISPLAY.toUByte()) // clear display, set cursor position to zero
        delay(2000) // this command takes a long time!
    }


   suspend fun home() {
        command(LCD_RETURNHOME.toUByte()) // set cursor position to zero
        delay(2000) // this command takes a long time!
    }

    /**
     * Установка позиции курсора.
     * При записи курасор устанавливается после поледнего записываемого символа
     * @param col номер столбца
     * @param row_ номер строки
     */
   suspend fun setCursor(col: UByte, row_: UByte) {
        val row_offsets =intArrayOf(0x00, 0x40, 0x14, 0x54)
       var row=row_
        if (row > _numlines.toUInt()) {
            row =( _numlines - 1).toUByte() // we count rows starting w/0
        }
       Log.d("BroLcd_","row=$row")
       Log.d("BroLcd_","off=${col + row_offsets[row.toInt()].toUByte()}")
        command(LCD_SETDDRAMADDR.toUByte() or (col + row_offsets[row.toInt()].toUByte()).toUByte())
//       command(0b000_1u or (col + row_offsets[row.toInt()].toUByte()))

    }

   suspend fun display() {
        _displaycontrol = _displaycontrol or LCD_DISPLAYON.toUByte()
        command(LCD_DISPLAYCONTROL.toUByte() or _displaycontrol.toUByte())
    }

    /**
     * Инициализация LCD-дисплея, занимает более 10.2 секунды
     * @param lines число строк в дисплее (по умолчанию 2)
     * @param dotsize
     */
   suspend fun begin( lines:UByte=2u) {
        val  dotsize:UByte=0u //размер точки (наверное надо разобраться и удалить)

       _displayfunction = (LCD_4BITMODE or LCD_1LINE or LCD_5x8DOTS).toUByte()
        if (lines > 1u) {
            _displayfunction =_displayfunction or LCD_2LINE.toUByte()
        }
        _numlines = lines.toInt();

        // for some 1 line displays you can select a 10 pixel high font
        if ((dotsize.toInt() != 0) && (lines.toInt() == 1)) {
            _displayfunction = _displayfunction or LCD_5x10DOTS.toUByte()
        }

        // SEE PAGE 45/46 FOR INITIALIZATION SPECIFICATION!
        // according to datasheet, we need at least 40ms after power rises above 2.7V
        // before sending commands. Arduino can turn on way befer 4.5V so we'll wait 50
        delay(50);

        // Now we pull both RS and R/W low to begin commands
        expanderWrite(if(isBacklight) 0b0000_1000.toUByte() else 0.toUByte() );	// reset expanderand turn backlight off (Bit 8 =1)
        delay(1000);

        //put the LCD into 4 bit mode
        // this is according to the hitachi HD44780 datasheet
        // figure 24, pg 46

        // we start in 8bit mode, try to set 4 bit mode
        write4bits((0x03 shl 4).toUByte());
        delay(4500); // wait min 4.1ms

        // second try
        write4bits((0x03 shl  4).toUByte());
        delay(4500); // wait min 4.1ms

        // third go!
        write4bits((0x03 shl  4).toUByte());
        delay(150);

        // finally, set to 4-bit interface
        write4bits((0x02 shl  4).toUByte());

        // set # lines, font size, etc.
        command(LCD_FUNCTIONSET.toUByte() or _displayfunction);

        // turn the display on with no cursor or blinking default
        _displaycontrol = LCD_DISPLAYON.toUByte() or LCD_CURSOROFF.toUByte() or LCD_BLINKOFF.toUByte()
        display();

        // clear it off
        clear();

        // Initialize to default text direction (for roman languages)
        _displaymode = LCD_ENTRYLEFT.toUByte() or  LCD_ENTRYSHIFTDECREMENT.toUByte()

        // set the entry mode
        command(LCD_ENTRYMODESET.toUByte() or _displaymode.toUByte());

        home();
    }

    init {


    }
}