package com.fardeenkhan.moodtune.config

class NoInternetException(message: String = "No internet connection") : java.net.UnknownHostException(message)

class ConfigNotAvailableException(message: String = "API keys could not be retrieved") : Exception(message)
