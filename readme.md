# Maintain

`com.tmtek.rx.Maintain`

Maintain is a class that builds upon rx Single instances with the `.latest()` operator designed to store and update emitted data based on defined conditions.


## The `Maintain.latest()` operator:

Consider the following stream:

```
//mCounter = 0 on init.

final Single<Long> exampleStream = Single.defer(() ->
	Single.just(mCounter++)
)
.delay(3, TimeUnit.SECONDS)
			
```

Every time I subscribe to that Single, the work would be performed, and the value would be emitted to me:

```
exampleStream.subscribe(value -> {
	//value would be 0
})

exampleStream.subscribe(value -> {
	//value would be 1
})

Single.timer(2)
	.flatMap(millis -> exampleStream)
	subscribe(value -> {
		//value would be 2
	})
	
Single.timer(4)
	.flatMap(millis -> exampleStream)
	subscribe(value -> {
		//value would be 3
	})	

```

But by adding `Maintain.latest()` something differ occurs:

```
//mCounter = 0 on init.

final Single<Long> exampleStream = Single.defer(() ->
	Single.just(mCounter++)
)
.delay(3, TimeUnit.SECONDS)
.compose(Maintain.latest())

``` 
The work is only performed once, and all recieve a value of 0:

```
exampleStream.subscribe(value -> {
	//value would be 0
})

exampleStream.subscribe(value -> {
	//value would be 0
})

Single.timer(2)
	.flatMap(millis -> exampleStream)
	subscribe(value -> {
		//value would be 0
	})
	
Single.timer(4)
	.flatMap(millis -> exampleStream)
	subscribe(value -> {
		//value would be 0
	})	

```

`Maintain.latest()` makes it so that all 4 subscriptions will recieve the stored value unless an Update condition allows an update to occur. It's important to understand the sequence of events for the above example:

1. The first subscription **kicks off the work**, and waits for the value.
1. The second subscription sees the **work is happening** so it waits for the value.
1. The third subscription sees that **work is happening** so it waits for the value.
1. The fourth subscription sees that **a value has been stored** and receives it.

The work only gets done once, because of the update function we are using. When we call `Maintain.latest()`, we are actually calling an overloaded version of the method that actually looks like this: 

`Maintain.latest(Maintain.Update.once())` 

That argument is what we refer to as the "update function". It is checked every time a subscription is made to the maintained stream, and work is not currently being performed.

If we used a different update function such as: `Maintain.Update.always()`, then our example behaves differently:

```
exampleStream.subscribe(value -> {
	//value would be 0
})

exampleStream.subscribe(value -> {
	//value would be 0
})

Single.timer(2)
	.flatMap(millis -> exampleStream.subscribe)
	subscribe(value -> {
		//value would be 0
	})
	
Single.timer(4)
	.flatMap(millis -> exampleStream.subscribe)
	subscribe(value -> {
		//value would be 1
	})	

```

Here's the sequence of events:

1. The first subscription **kicks off the work**, and waits for the value.
1. The second subscription **sees the work is happening** so it waits for the value.
1. The third subscription **sees that work is happening** so it waits for the value.
1. The fourth subscription **sees that no work is happening but Maintain wants to update always**. New work kicks off and the value is returned.

## Update functions:

An update function is the argument we supply to the `Maintain.latest(updateFunc)` operator. These functions have the following signature as a lambda:

`(previousEmission, systemTimeMillisOfLastUpdate) -> {return Single.just(bool)}`

`previousEmission` is the value that was emitted from the last run of the upstream Single. This value will never be null, because Maintain.latest will always update once if no value is being stored. You are free to inspect the previous emission in your update function if you intend to make a decision about whether or not to update based on the contents of that emission.

`systemTimeMillisOfLastUpdate` is the system time in milliseconds when the last emission was emitted. This value allows you to perform updates based on expiry. 

Your update functions must return a `Single<Boolean>`, and the value emitted from it determines whether or not the update is allowed to happen.

###Maintain.Update:

Here is a list of the Update functions baked into Maintain:

#### `Maintain.Update.once()`

Allows the maintained stream to update once on first subscription, but never again for it's lifetime.

#### `Maintain.Update.always()`

The maintained stream will update every time it is subscribed to as long as work is not being performed. If work is being performed, all subscribers will share the result.

#### `Maintain.Update.expired(period, TimeUnit)`

The maintained stream will be allowed to update if the last update happened before the time span specified measured from now.

#### `Maintain.Update.when(Single<Boolean>)`

The maintained stream will be allowed to update if the supplied Single emits a value of true.


#### `Maintain.Update.toggledOn(Toggle)`

The maintained stream will be allowed to update if the current state of the supplied Toggle is currently "on". If so, the stream will update, and the Toggle will be set back to "off".

Toggles give you the ability to mark your maintained stream for update which will take place on the next subscription.

```
final Maintain.Toggle toggle = new Maintain.Toggle();

//mCounter = 0 on init.

final Single<Long> exampleStream = Single.defer(() ->
	Single.just(mCounter++)
)
.delay(3, TimeUnit.SECONDS)
.compose(Maintain.latest(Maintain.Update.toggledOn(toggle)))

//toggle.on() : marks for update.
//toggle.off() : unmarks update.

```

#### `Maintain.Update.conditions(requireAll, updateFuncs...)`

This update function allows you to specify many conditions that dictate whether or not the maintained stream updates. The arguments you supply are as follows:

`requireAll` Is a boolean value that specifies whether or not all conditions must return true, in order for there to be an update. If set to true, all conditions must return true, if set to false, only one condition needs to return true.

`updateFuncs` You may supply any number of other update functions (even other instances of Maintain.Update.conditions()) to be part of this group of conditions.

```

final Maintain.Toggle toggle = new Maintain.Toggle();

//mCounter = 0 on init.

final Single<Long> exampleStream = Single.defer(() ->
	Single.just(mCounter++)
)
.delay(3, TimeUnit.SECONDS)
.compose(Maintain.latest(
	Maintain.Update.conditions(
		false,
		Maintain.Update.toggledOn(toggle),
		Maintain.Update.expired(60, TimeUnit.SECONDS)
	)
))
```

In the above example, the stream will update if the toggle has been toggled on, or if 60 seconds has passed since the last update. If the boolean value was switched to true, then an update would only happen if both conditions are true.

## Subscribing to all updates:

If you need to subscribe to a maintained stream to capture each update as an emission (like a traditional Observable), you can do so by exposing the BehaviorSubject it uses to store it's values:

`Maintain.latest(updateFunc, subject)`

The second optional argument to the method is a BehaviourSubject that will be updated with all of the values the upstream emits. If you don't supply a BehaviorSubject, Maintain.latest creates one internally.

```
//mCounter = 0 on init.

final Single<Long> exampleStream = Single.defer(() ->
	Single.just(mCounter++)
)
.delay(3, TimeUnit.SECONDS)
.compose(Maintain.latest(
	Maintain.Update.always(),
	
))

```


