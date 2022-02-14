package com.seanshubin.learn.cdk.console

import com.seanshubin.learn.cdk.domain.Runner

object EntryPoint {
    @JvmStatic
    fun main(args: Array<String>) {
        Runner().run()
    }
}