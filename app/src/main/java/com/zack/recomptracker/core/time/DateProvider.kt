package com.zack.recomptracker.core.time

import java.time.LocalDate

interface DateProvider {
    fun today(): LocalDate
}

class SystemDateProvider : DateProvider {
    override fun today(): LocalDate = LocalDate.now()
}
