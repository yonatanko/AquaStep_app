#include <Wire.h>
#include <Adafruit_Sensor.h>
#include <Adafruit_LSM303_U.h>
#include "BluetoothSerial.h"
#include <sys/time.h>
#include <stdio.h>

/* Assign a unique ID to this sensor at the same time */
Adafruit_LSM303_Accel_Unified accel = Adafruit_LSM303_Accel_Unified(54321);

#if !defined(CONFIG_BT_ENABLED) || !defined(CONFIG_BLUEDROID_ENABLED)
#error Bluetooth is not enabled! Please run `make menuconfig` to and enable it
#endif

BluetoothSerial SerialBT;


unsigned long start_time = millis();
const int temp_resistor = 32;
int temp_value = 0;
float m = 10.0; //Milli-volt per C
float v1 = 500.0; //Offset

double ReadVoltage(byte pin) {
  double reading = analogRead(pin); // Reference voltage is 3v3 so maximum reading is 3v3 = 4095 in range 0 to 4095
  if (reading < 1 || reading > 4095) return 0;
  return -0.000000000000016 * pow(reading, 4) + 0.000000000118171 * pow(reading, 3) - 0.000000301211691 * pow(reading, 2) +
  0.001109019271794 * reading + 0.034143524634089;
}

void setup(void)
{
#ifndef ESP8266
  while (!Serial);     // will pause Zero, Leonardo, etc until serial console opens
#endif
  pinMode(temp_resistor, OUTPUT);
  Serial.begin(115200);
  Serial.println("Accelerometer Test"); Serial.println("");

  /* Initialise the sensor */
  if(!accel.begin())
  {
    /* There was a problem detecting the ADXL345 ... check your connections */
    Serial.println("Ooops, no LSM303 detected ... Check your wiring!");
    while(1);
  }
//
  SerialBT.begin("AquaStepBT"); //Bluetooth device name
  Serial.println("The device started, now you can pair it with bluetooth!");

}

void loop(void)
{
  /* Get a new sensor event */
  sensors_event_t event;
  accel.getEvent(&event);

  unsigned long end_time = millis();
  unsigned long elapsed = (end_time-start_time);
  float val = 1000.0 * ReadVoltage(temp_resistor);
  float T = (val-v1)/m;
  Serial.println((String)" " + event.acceleration.x + ", " + event.acceleration.y + ", " + event.acceleration.z + ", " + elapsed + ", " + T);
  SerialBT.println((String)" " + event.acceleration.x + ", " + event.acceleration.y + ", " + event.acceleration.z + ", " + elapsed + ", " + T);

  /* Delay before the next sample */
  delay(70);
}
