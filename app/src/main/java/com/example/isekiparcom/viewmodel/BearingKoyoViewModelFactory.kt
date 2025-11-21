// app/src/main/java/com/example/isekiparcom/viewmodel/BearingKoyoViewModelFactory.kt

package com.example.isekiparcom.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class BearingKoyoViewModelFactory(
    private val context: Context
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(BearingKoyoViewModel::class.java)) {
            return BearingKoyoViewModel(context) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}