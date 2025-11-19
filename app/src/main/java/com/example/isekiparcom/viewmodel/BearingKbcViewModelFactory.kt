// app/src/main/java/com/example/isekiparcom/viewmodel/BearingKbcViewModelFactory.kt

package com.example.isekiparcom.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class BearingKbcViewModelFactory(
    private val context: Context
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(BearingKbcViewModel::class.java)) {
            return BearingKbcViewModel(context) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}