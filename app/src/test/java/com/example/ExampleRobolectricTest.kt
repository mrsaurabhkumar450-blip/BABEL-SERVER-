package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.ai.ActionType
import com.example.ai.JarvisAiEngine
import com.example.security.EncryptionManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("JARVIS AI", appName)
  }

  @Test
  fun `test encryption and decryption roundtrip`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val manager = EncryptionManager(context)
    val secret = "Confidential Jarvis Order 123"
    val encrypted = manager.encrypt(secret)

    assertNotEquals(secret, encrypted)
    val decrypted = manager.decrypt(encrypted)
    assertEquals(secret, decrypted)
  }

  @Test
  fun `test pin security setup and verification`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val manager = EncryptionManager(context)

    assertTrue(manager.setPin("7890"))
    assertTrue(manager.verifyPin("7890"))
    assertFalse(manager.verifyPin("1111"))
  }

  @Test
  fun `test command classifier for device actions`() {
    val engine = JarvisAiEngine()

    val torchOn = engine.parseDeviceAction("Jarvis, torch on karo please")
    assertEquals(ActionType.TORCH_ON, torchOn.type)

    val torchOff = engine.parseDeviceAction("turn off flashlight")
    assertEquals(ActionType.TORCH_OFF, torchOff.type)

    val whatsapp = engine.parseDeviceAction("send whatsapp message to 9876543210 saying meeting start")
    assertEquals(ActionType.SEND_WHATSAPP, whatsapp.type)
    assertEquals("9876543210", whatsapp.targetPhone)

    val battery = engine.parseDeviceAction("battery kitni hai check karo")
    assertEquals(ActionType.BATTERY_CHECK, battery.type)

    val volumeMax = engine.parseDeviceAction("volume full karo")
    assertEquals(ActionType.VOLUME_MAX, volumeMax.type)
  }
}
