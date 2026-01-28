package app.tktn.core_service.base

import kotlinx.coroutines.Dispatchers

actual val kotlinx.coroutines.Dispatchers.IO: kotlinx.coroutines.CoroutineDispatcher
	get() = Dispatchers.Main