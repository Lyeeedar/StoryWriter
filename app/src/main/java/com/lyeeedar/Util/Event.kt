package com.lyeeedar.Util

enum class HandlerAction
{
	KeepAttached,
	Detach
}

class Event0Arg {
	private val handlers = ArrayList<(() -> HandlerAction)>()

	operator fun plusAssign(handler: () -> HandlerAction)
	{
		if (invoking) throw Exception("Cannot add handlers during invoke!")
		handlers.add(handler)
	}

	operator fun minusAssign(handler: () -> HandlerAction)
	{
		if (invoking) throw Exception("Cannot remove handlers during invoke!")
		handlers.remove(handler)
	}

	var invoking = false
	operator fun invoke()
	{
		invoking = true

		val itr = handlers.iterator()
		while (itr.hasNext())
		{
			val handler = itr.next()
			if (handler.invoke() == HandlerAction.Detach) itr.remove()
		}

		invoking = false
	}

	fun clear()
	{
		handlers.clear()
	}
}

class Event1Arg<T> {
	private val handlers = ArrayList<((T) -> HandlerAction)>()

	operator fun plusAssign(handler: (T) -> HandlerAction)
	{
		if (invoking) throw Exception("Cannot add handlers during invoke!")
		handlers.add(handler)
	}

	operator fun minusAssign(handler: (T) -> HandlerAction)
	{
		if (invoking) throw Exception("Cannot remove handlers during invoke!")
		handlers.remove(handler)
	}

	var invoking = false
	operator fun invoke(value: T)
	{
		invoking = true

		val itr = handlers.iterator()
		while (itr.hasNext())
		{
			val handler = itr.next()
			if (handler.invoke(value) == HandlerAction.Detach) itr.remove()
		}

		invoking = false
	}

	fun clear()
	{
		handlers.clear()
	}
}

class Event2Arg<T1, T2> {
	private val handlers = ArrayList<((T1, T2) -> HandlerAction)>()

	operator fun plusAssign(handler: (T1, T2) -> HandlerAction)
	{
		if (invoking) throw Exception("Cannot add handlers during invoke!")
		handlers.add(handler)
	}

	operator fun minusAssign(handler: (T1, T2) -> HandlerAction)
	{
		if (invoking) throw Exception("Cannot remove handlers during invoke!")
		handlers.remove(handler)
	}

	var invoking = false
	operator fun invoke(value1: T1, value2: T2)
	{
		invoking = true

		val itr = handlers.iterator()
		while (itr.hasNext())
		{
			val handler = itr.next()
			if (handler.invoke(value1, value2) == HandlerAction.Detach) itr.remove()
		}

		invoking = false
	}

	fun clear()
	{
		handlers.clear()
	}
}