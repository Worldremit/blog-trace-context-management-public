#TL;DR

Distributed trace context propagation breaks when tasks are saved in a repository.
Brave context can be exported to `Map<String, String>` and restored from such a map. It allows storing tracing context and a task in the repository and restoring it when the task is executed.

An implementation in Kotlin with a sample application is available [here](https://github.com/Worldremit/blog-trace-context-management).

# Background

[The distributed tracing](https://opentracing.io/docs/overview/what-is-tracing/) helps monitor, profile and debug requests that are handled by many (micro)services.
It is based on a tracing context that is passed with each call.
The way the context is passed depends on the transport protocol used. The most common way is the usage of headers (REST, messaging).

The management of the tracing context should be transparent to the rest of the service. It should be an aspect shared by all microservices, so the context is not "lost" when a request is sent to a service that does not support tracing.
Most implementations require some configuration and then tracing propagation magic happens.

# Problem

The tracing context propagation works if the calls between services are uninterrupted.
The propagation may be broken when a service executes some part of the request later in a different thread.
An example is saving a task to be executed later in DB. Another one is sending a message using an outbox pattern -
which means saving a request to send a message in a DB and then sending the actual message asynchronously.

The saving to DB breaks the context propagation as context is only available at runtime, and DBs do not support storing
of such context with records.

# Solution

The service has to do what the library does:
* store the context whenever data connected with context is saved
* restore the context from saved data when data is processed

# Implementation
Inevitably we have to get a bit more familiar with our solution for handling tracing context as we will have to store and restore the context from our code.
At WorldRemit, we use [Spring Cloud Sleuth](https://spring.io/projects/spring-cloud-sleuth), which provides Spring Boot auto-configuration for distributed tracing
based on [Brave](https://github.com/openzipkin/brave), which is a distributed tracing instrumentation library.

We have to find a way to externalize the context to a format that can be easily serialized and persisted based on Brave API.

## Functional Programming

My team builds services based on the Functional Programming paradigm.  The implementation will follow its main principle: *Functional programming is programming as if functions really mattered*.

## API

Even though we are working with *Brave*, we would like an API that is not bound to a specific implementation and is easy to mock in tests:
* a data class to hold the context as a map of strings
```kotlin
data class TracingContext(val value: Map<String, String>)
```
* context capturing function type
```kotlin
typealias CaptureTracingContext = () -> TracingContext
````
* function type for running a block of code in the trace context restored from the captured one
```kotlin
typealias RunInTracingContext<T> = (TracingContext, () -> T) -> T
```
* function type for creating a coroutine context responsible for setting the trace context
```kotlin
typealias CreateTracingCoroutineContext = (TracingContext) -> CoroutineContext
```

## Persisting and Restoring the Context

If you need to persist the tracing context along with a task to the repository, you need:
1. capture the context
2. serialize the context into a form that can be saved to your repository
3. add the serialized context to the entry that is stored in the repository

```kotlin
val jsonTracingContext = toJson(captureTracingContext())

// entity creation
entity.tracingContext = jsonTracingContext

storeEntity(entity)
```

We serialize the `TracingContext`'s map into JSON and put it into a field of entity stored in DB.
It's a simplistic solution, but the context is part of the task, and JSON is just good enough for storing a map of strings.

When the task is run, we need to restore the `TracingContext` with the map restored from JSON and run the task
within the context. Remember to run each task in a separate trace context.

```kotlin
//read entity 

val tracingContext = TracingContext(fromJson(entity.tracingContext))

//option 1 - run block
runInTracingContext(tracingContext) {
    //run task restored from entity
}

//option 2 - run in coroutine context
withContext(createTracingCoroutineContext(tracingContext)) {
  //run task restored from entity
}
```

## Brave's Key Elements
Brave uses following components to manage the context:
* [Tracing](https://www.javadoc.io/doc/io.zipkin.brave/brave/latest/brave/Tracing.html) - the entry point for trace management - keeps current context, provides injector and extractor
* [TraceContext](https://www.javadoc.io/doc/io.zipkin.brave/brave/latest/brave/propagation/TraceContext.html) - the actual trace context representation
* [TraceContext.Extractor](https://www.javadoc.io/doc/io.zipkin.brave/brave/latest/brave/propagation/TraceContext.Extractor.html) responsible for extracting the context from a request
* [TraceContext.Injector](https://www.javadoc.io/doc/io.zipkin.brave/brave/latest/brave/propagation/TraceContext.Injector.html) responsible for filling a request with the context
* [Request](https://www.javadoc.io/doc/io.zipkin.brave/brave/latest/brave/Request.html) - represents an abstract entity capable of transferring the context
* [Propagation.Getter](https://www.javadoc.io/doc/io.zipkin.brave/brave/latest/brave/propagation/Propagation.Getter.html) a getter implementation that "knows" how to retrieve a value by key from specific request
* [Propagation.Setter](https://www.javadoc.io/doc/io.zipkin.brave/brave/latest/brave/propagation/Propagation.Setter.html) a setter implementation that "knows" how to populate the specific request with a key and a value

## Managing the Context

Thanks to good design of Brave the only things that we need to provide on our side to be able to extract and restore the `TraceContext` are:
* request implementation `MapRequest`:
```kotlin
class MapRequest(initial: Map<String, String> = mapOf()) : Request() {
    private val mapping: MutableMap<String, String> = initial.toMutableMap()
    
    override fun spanKind(): Span.Kind = Span.Kind.SERVER

    override fun unwrap(): Any = context

    val context: Map<String, String> get() = mapping.toMap()

    fun setContext(key: String, value: String) {
        mapping[key] = value
    }

    fun getContext(key: String): String? = mapping[key]
}
```

It simply keeps internal map `mapping` that can be changed or accessed using `setContext` and `getContext` methods.

* getter

```kotlin
object MapRequestGetter : Propagation.Getter<MapRequest, String> {
    override fun get(request: MapRequest, key: String): String? = request.getContext(key)
}
```

* setter

```kotlin
object MapRequestSetter : Propagation.Setter<MapRequest, String> {
    override fun put(request: MapRequest, key: String, value: String) {
        request.setContext(key, value)
    }
}
``` 

To externalize the context to our `TracingContext`, we simply have to provide Brave with our empty custom request into which it can inject current `TraceContext.`

```kotlin
fun captureBraveTracingContext(tracing: Tracing): TracingContext {
    val traceContext = tracing.currentTraceContext().get() ?: return TracingContext(emptyMap())
    val request = MapRequest()
    tracing.propagation().injector(MapRequestSetter).inject(traceContext, request)
    return TracingContext(request.context)
}
```

The key line is:

```kotlin
tracing.propagation().injector(MapRequestSetter).inject(traceContext, request)
```

## Running in Context

We have to do an opposite operation to restore `TraceContext` from `TracingContext`. Provide Brave with prefilled custom request basing on which it will recreate `TraceContext` if possible:

```kotlin
fun createBraveTraceContext(tracing: Tracing, context: TracingContext): TraceContext? =
    tracing.propagation().extractor(MapRequestGetter)
        .extract(MapRequest(context.value))
        .context()
```

### Block

You can run a block of code in scope of provided `TracingContext`.
This is the preferred way if your processing is based on threads.
```kotlin
fun <R> runInTracingContext(
    createTraceContext: CreateTraceContext,
    tracing: Tracing,
    tracingContext: TracingContext,
    block: () -> R
): R {
    val restoredScope = nullable.eager<Scope> {
        val context = createTraceContext(tracing, tracingContext).bind()
        val currentTraceContext = currentTraceContext().bind()
        currentTraceContext.newScope(context)
    }
    val activeScope = restoredScope ?: Scope.NOOP
    return activeScope.use {
        block()
    }
}
```

### Coroutines

When you run your code in coroutines, you can use a special context that is
responsible for managing the tracing.

```kotlin
fun createBraveTracingCoroutineContext(
    createTraceContext: CreateTraceContext,
    tracing: Tracing,
    context: TracingContext
): CoroutineContext =
    createTraceContext(tracing, context)
        .toOption()
        .map(::TraceContextElement)
        .getOrElse { EmptyCoroutineContext }
```

The `TraceContextElement` is responsible for restoring the `TraceContext` via creation of a new scope.

```kotlin
class TraceContextElement(
    val context: TraceContext? = traceContext()
) : ThreadContextElement<Scope>, AbstractCoroutineContextElement(Key) {
    private val logger = KotlinLogging.logger {}

    companion object Key : CoroutineContext.Key<TraceContextElement>

    init {
        logger.debug("Initialized to: <$context> in thread - id: <${currentThread().id}>, name: <${currentThread().name}>")
    }

    override fun updateThreadContext(context: CoroutineContext): Scope {
        val scope = currentTraceContext()?.newScope(this.context) ?: Scope.NOOP
        logger.debug("Registered new scope: <$scope> for traceContext: <${this.context}> in thread - id: <${currentThread().id}>, name: <${currentThread().name}>")
        return scope
    }

    override fun restoreThreadContext(context: CoroutineContext, oldState: Scope) {
        logger.debug("Restoring traceContext by closing scope: <$oldState> in thread - id: <${currentThread().id}>, name: <${currentThread().name}>")
        oldState.close()
    }

    private fun currentThread() = Thread.currentThread()
}
```

## Configuration

Because of the FP approach, our implementation is a bunch of functions that need to be
configured to meet the API contract. We use SpringBoot for application assembly.
Functions are registered and referenced by type in the same way as it is done with
objects and interfaces.

```kotlin
@Configuration
class TracingConfiguration {

    @Bean
    fun createBraveTraceContextFun(): CreateTraceContext = ::createBraveTraceContext

    @Bean
    fun captureTracingContextFun(tracing: Tracing): CaptureTracingContext =
        ::captureBraveTracingContext.partially1(tracing)

    @Bean
    fun createTracingCoroutineContext(
        createTraceContext: CreateTraceContext,
        tracing: Tracing
    ): CreateTracingCoroutineContext =
        ::createBraveTracingCoroutineContext.curried()(createTraceContext)(tracing)
}

```

# Source Code

The source is available on GitHub in this [repository](https://github.com/Worldremit/blog-trace-context-management).

# Demo Application

I have created a sample application that uses described context management.

It simulates the processing of a command which incorporates sending of a message via outbox.

The application handles the creation of a user. The user is saved in a repository, and also an event is published via outbox.

The events sent via outbox are first saved to a repository and then sent to a topic.
The publishing from outbox is initiated from a scheduler that works in a separate thread pool.

## Controller

When an external request or message reaches our service Sleuth & Brave create new or restore context provided in transport.

We want to "capture" the MDC and trace contexts into a new coroutine context it is used in further processing.

```kotlin
        withContext(MDCContext() + TraceContextElement()) {
            // processing
        }   

```
Handling the creation of user
```kotlin
    @PutMapping("/{id}")
    suspend fun sendMessage(@PathVariable id: String, @RequestBody dto: UserDto): ResponseEntity<String> =
        withContext(MDCContext() + TraceContextElement()) {
            logger.info { "UserDto: $dto" }
            val command = CreateUserCommand(UserId(UUID.fromString(id)), Login(dto.login), name(dto.name))
            createUser(command).fold(
                { error -> ResponseEntity.badRequest().body("Something went wrong: $error") },
                { result -> ResponseEntity.ok().body(result.toString()) }
            )
        }
```

## Outbox

The outbox is responsible for storing messages in its repository and later sending messages in an asynchronous way.

### Storing the message

When a message is stored, the context has to be extracted and saved with the message.

```kotlin
@OptIn(ExperimentalTime::class)
suspend fun sendMessage(
    storeEntry: StoreEntry,
    captureTracingContext: CaptureTracingContext,
    message: Message
): Either<SendError, Unit> {
    logger.info { "Storing message: $message" }
    delay(1)
    val context = captureTracingContext()
    val entry = MessageEntry(EntryId(UUID.randomUUID()), message, context.value)
    return storeEntry(entry)
        .map { }
        .mapLeft { SendError.Persistence }
}
```

### Delivering the message

Delivering is executed from a scheduler. When there is a message to send, the trace context has to be restored and
the delivery to a topic is run in the same `TraceContext` the request to create the user.

```kotlin
suspend fun deliverMessage(
    acquireEntry: AcquireEntry,
    releaseEntry: ReleaseEntry,
    removeEntry: RemoveEntry,
    deliverToTopic: DeliverToTopic,
    createTracingContext: CreateTracingCoroutineContext,
): Either<DeliveryError, Unit> = either {
    val entry: MessageEntry? = acquireEntry().mapLeft(::mapError).bind()
    if (entry != null) {
        logger.info { "Delivering entry: $entry" }
        withContext(createTracingContext(TracingContext(entry.tracingContext))) {
            val message = entry.message
            logger.info { "Delivering message: $message" }
            val deliverResult = deliverToTopic(message.topic, message.payload)
            deliverResult.fold(
                { releaseEntry(entry).mapLeft(::mapError).bind()},
                { removeEntry(entry).mapLeft(::mapError).bind() }
            )
        }
    }
}
```

## UseCase Execution

### Start application

```shell
./gradlew bootRun
```

### Request

```shell
curl --location --request PUT 'http://localhost:8080/users/2ee0b1c1-9fc0-4cbf-ad39-a8beee22e9b6' \
--header 'Content-Type: application/json' \
--data-raw '{
    "login": "Fabiola54",
    "name": {
        "first": "Ayana",
        "middle":"Lucy",
        "last": "Fisher"

    }
}'
```

### Logs
The logs include trace context, so you can observe that the trace context is correctly "transferred" to the thread that performs the actual delivery of the message.

The trace context is logged in the first square brackets, the tread name in the next ones.
```shell
2021-11-10 22:32:20.395  INFO [,fa636d3ed78818d4,fa636d3ed78818d4] [   XNIO-1 I/O-6] c.w.s.user.adapter.rest.UserController   : UserDto: UserDto(login=Amya95, name=NameDto(first=Kareem, middle=Guido, last=Prohaska))
2021-11-10 22:32:21.411  INFO [,fa636d3ed78818d4,fa636d3ed78818d4] [DefaultExecutor] c.w.sample.user.store.UserStore          : User stored: User(id=UserId(value=49df2a87-36dd-4e91-a45e-916416a03b9f), login=Login(value=Amya95), name=Name(first=FirstName(value=Kareem), middleName=MiddleName(value=Guido), lastName=LastName(value=Prohaska)))
2021-11-10 22:32:21.415  INFO [,fa636d3ed78818d4,fa636d3ed78818d4] [DefaultExecutor] com.worldremit.base.outbox.Outbox        : Storing message: Message(topic=Topic(value=user.events), payload=Payload(value=UserCreatedEvent(timestamp=2021-11-10T21:32:21.412643Z, user=User(id=UserId(value=49df2a87-36dd-4e91-a45e-916416a03b9f), login=Login(value=Amya95), name=Name(first=FirstName(value=Kareem), middleName=MiddleName(value=Guido), lastName=LastName(value=Prohaska))))))
2021-11-10 22:32:21.423  INFO [,fa636d3ed78818d4,fa636d3ed78818d4] [pool-2-thread-1] c.w.base.outbox.InMemoryEntryStore       : Storing: MessageEntry(id=EntryId(value=78f2085a-8f9e-48f3-94e3-b8a9442ebb38), message=Message(topic=Topic(value=user.events), payload=Payload(value=UserCreatedEvent(timestamp=2021-11-10T21:32:21.412643Z, user=User(id=UserId(value=49df2a87-36dd-4e91-a45e-916416a03b9f), login=Login(value=Amya95), name=Name(first=FirstName(value=Kareem), middleName=MiddleName(value=Guido), lastName=LastName(value=Prohaska)))))), tracingContext={b3=fa636d3ed78818d4-fa636d3ed78818d4-0})
2021-11-10 22:32:22.285  INFO [,,] [pool-2-thread-2] c.w.base.outbox.InMemoryEntryStore       : Acquire result: Either.Right(MessageEntry(id=EntryId(value=78f2085a-8f9e-48f3-94e3-b8a9442ebb38), message=Message(topic=Topic(value=user.events), payload=Payload(value=UserCreatedEvent(timestamp=2021-11-10T21:32:21.412643Z, user=User(id=UserId(value=49df2a87-36dd-4e91-a45e-916416a03b9f), login=Login(value=Amya95), name=Name(first=FirstName(value=Kareem), middleName=MiddleName(value=Guido), lastName=LastName(value=Prohaska)))))), tracingContext={b3=fa636d3ed78818d4-fa636d3ed78818d4-0}))
2021-11-10 22:32:22.287  INFO [,f22230d078921fba,f22230d078921fba] [   scheduling-1] com.worldremit.base.outbox.Outbox        : Delivering entry: MessageEntry(id=EntryId(value=78f2085a-8f9e-48f3-94e3-b8a9442ebb38), message=Message(topic=Topic(value=user.events), payload=Payload(value=UserCreatedEvent(timestamp=2021-11-10T21:32:21.412643Z, user=User(id=UserId(value=49df2a87-36dd-4e91-a45e-916416a03b9f), login=Login(value=Amya95), name=Name(first=FirstName(value=Kareem), middleName=MiddleName(value=Guido), lastName=LastName(value=Prohaska)))))), tracingContext={b3=fa636d3ed78818d4-fa636d3ed78818d4-0})
2021-11-10 22:32:22.298  INFO [,fa636d3ed78818d4,fa636d3ed78818d4] [   scheduling-1] com.worldremit.base.outbox.Outbox        : Delivering message: Message(topic=Topic(value=user.events), payload=Payload(value=UserCreatedEvent(timestamp=2021-11-10T21:32:21.412643Z, user=User(id=UserId(value=49df2a87-36dd-4e91-a45e-916416a03b9f), login=Login(value=Amya95), name=Name(first=FirstName(value=Kareem), middleName=MiddleName(value=Guido), lastName=LastName(value=Prohaska))))))
2021-11-10 22:32:23.311  INFO [,fa636d3ed78818d4,fa636d3ed78818d4] [   scheduling-1] com.worldremit.base.outbox.Outbox        : Deliver to topic: Topic(value=user.events), payload: Payload(value=UserCreatedEvent(timestamp=2021-11-10T21:32:21.412643Z, user=User(id=UserId(value=49df2a87-36dd-4e91-a45e-916416a03b9f), login=Login(value=Amya95), name=Name(first=FirstName(value=Kareem), middleName=MiddleName(value=Guido), lastName=LastName(value=Prohaska))))) succeeded
2021-11-10 22:32:23.314  INFO [,fa636d3ed78818d4,fa636d3ed78818d4] [pool-2-thread-1] c.w.base.outbox.InMemoryEntryStore       : Removing entry: MessageEntry(id=EntryId(value=78f2085a-8f9e-48f3-94e3-b8a9442ebb38), message=Message(topic=Topic(value=user.events), payload=Payload(value=UserCreatedEvent(timestamp=2021-11-10T21:32:21.412643Z, user=User(id=UserId(value=49df2a87-36dd-4e91-a45e-916416a03b9f), login=Login(value=Amya95), name=Name(first=FirstName(value=Kareem), middleName=MiddleName(value=Guido), lastName=LastName(value=Prohaska)))))), tracingContext={b3=fa636d3ed78818d4-fa636d3ed78818d4-0})
```





