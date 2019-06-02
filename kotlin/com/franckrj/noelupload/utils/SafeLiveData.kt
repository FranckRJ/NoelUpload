package com.franckrj.noelupload.utils

import androidx.lifecycle.LiveData

open class SafeLiveData<T : Any>(newVal: T) : LiveData<T>(newVal) {
    override fun getValue(): T {
        return super.getValue()!!
    }
}
