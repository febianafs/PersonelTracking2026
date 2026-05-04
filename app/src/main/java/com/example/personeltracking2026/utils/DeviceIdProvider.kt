//package com.example.personeltracking2026.utils
//
//import android.annotation.SuppressLint
//import android.content.Context
//import android.os.Build
//import android.provider.Settings
//
//object DeviceIdProvider {
//
//    @SuppressLint("HardwareIds", "MissingPermission")
//    fun getDeviceId(context: Context): String {
//        return try {
//            val serial = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
//                Build.getSerial()
//            } else {
//                Build.SERIAL
//            }
//
//            if (!serial.isNullOrEmpty() && serial != "unknown") {
//                serial
//            } else {
//                getAndroidId(context)
//            }
//
//        } catch (e: Exception) {
//            getAndroidId(context)
//        }
//    }
//
//    private fun getAndroidId(context: Context): String {
//        return Settings.Secure.getString(
//            context.contentResolver,
//            Settings.Secure.ANDROID_ID
//        ) ?: "UNKNOWN_DEVICE"
//    }
//}