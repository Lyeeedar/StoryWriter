package com.lyeeedar.Util

class Future
{
	companion object
	{
		private val pendingCalls = ArrayList<CallData>()

		private val queuedActions = ArrayList<()->Unit>()

		private var processing = false

		private var thread: Thread? = null

		fun update(delta: Float)
		{
			synchronized(processing)
			{
				if (processing)
					throw Exception("Nested update call!")

				processing = true
			}

			val itr = pendingCalls.iterator()
			while (itr.hasNext())
			{
				val item = itr.next()
				item.delay -= delta

				if (item.delay <= 0f)
				{
					itr.remove()
					item.function.invoke()
				}
			}

			synchronized(processing)
			{
				processing = false

				for (queued in queuedActions)
				{
					queued.invoke()
				}
				queuedActions.clear()
			}
		}

		fun call(function: () -> Unit, delay: Float, token: Any? = null)
		{
			startUpdaterThread()

			synchronized(processing)
			{
				if (processing)
				{
					queuedActions.add {
						call(function, delay, token)
					}
				}
				else
				{
					if (token != null)
					{
						cancel(token)
					}

					pendingCalls.add(CallData(function, delay, token))
				}
			}
		}

		fun cancel(token: Any)
		{
			startUpdaterThread()

			synchronized(processing)
			{
				if (processing)
				{
					queuedActions.add {
						cancel(token)
					}
				}
				else
				{
					val itr = pendingCalls.iterator()
					while (itr.hasNext())
					{
						val item = itr.next()

						if (item.token == token)
						{
							itr.remove()
						}
					}
				}
			}
		}

		fun startUpdaterThread() {
			if (thread != null) return

			thread = futureThread()
			thread!!.start()
		}
	}

	private class futureThread : Thread() {
		@Synchronized
		override fun run() {

			var time = System.currentTimeMillis()
			while (true) {
				val currentTime = System.currentTimeMillis()
				val delta = (currentTime - time).toFloat() / 1000f
				time = currentTime

				Future.update(delta)
			}
		}
	}
}

data class CallData(val function: () -> Unit, var delay: Float, val token: Any?)
