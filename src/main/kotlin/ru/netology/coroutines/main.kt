package ru.netology.coroutines

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.logging.HttpLoggingInterceptor
import ru.netology.coroutines.dto.*
import java.io.IOException
import java.util.HashMap
import java.util.concurrent.TimeUnit
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

private val gson = Gson()
private val BASE_URL = "http://127.0.0.1:9999"
private val client = OkHttpClient.Builder()
    .addInterceptor(HttpLoggingInterceptor(::println).apply {
        level = HttpLoggingInterceptor.Level.BODY
    })
    .connectTimeout(30, TimeUnit.SECONDS)
    .build()

fun main() {
    with(CoroutineScope(EmptyCoroutineContext)) {
        launch {
            try {
                var authorsMap = HashMap<Long, Author>()
                var authorsUnrequested = HashSet<Long>()
                val posts = getPosts(client)
                    .map { post ->

                        if (!authorsUnrequested.contains(post.authorId)) {
                            authorsUnrequested.add(post.authorId)
                            if (!authorsMap.containsKey(post.authorId))
                                authorsMap.putIfAbsent(post.authorId, getAuthor(client, post.authorId))
                        }

                        async {
                            
                            val postComments = getComments(client, post.id)
                            postComments.forEach {
                                if (!authorsUnrequested.contains(it.authorId)) {
                                    authorsUnrequested.add(it.authorId)
                                    if (!authorsMap.containsKey(it.authorId))
                                        authorsMap.putIfAbsent(it.authorId, getAuthor(client, it.authorId))
                                }
                            }
                            println("authors:$authorsMap")
                            PostWithComments(post, getComments(client, post.id))
                            PostWithCommentsAndAuthors(post, authorsMap, postComments)
                        }
                    }.awaitAll()

                println(posts)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    Thread.sleep(30_000L)
}

suspend fun OkHttpClient.apiCall(url: String): Response {
    return suspendCoroutine { continuation ->
        Request.Builder()
            .url(url)
            .build()
            .let(::newCall)
            .enqueue(object : Callback {
                override fun onResponse(call: Call, response: Response) {
                    continuation.resume(response)
                }

                override fun onFailure(call: Call, e: IOException) {
                    continuation.resumeWithException(e)
                }
            })
    }
}

suspend fun <T> makeRequest(url: String, client: OkHttpClient, typeToken: TypeToken<T>): T =
    withContext(Dispatchers.IO) {
        client.apiCall(url)
            .let { response ->
                if (!response.isSuccessful) {
                    response.close()
                    throw RuntimeException(response.message)
                }
                val body = response.body ?: throw RuntimeException("response body is null")
                gson.fromJson(body.string(), typeToken.type)
            }
    }

suspend fun getPosts(client: OkHttpClient): List<Post> =
    makeRequest("$BASE_URL/api/slow/posts", client, object : TypeToken<List<Post>>() {})

suspend fun getComments(client: OkHttpClient, id: Long): List<Comment> =
    makeRequest("$BASE_URL/api/slow/posts/$id/comments", client, object : TypeToken<List<Comment>>() {})

suspend fun getAuthor(client: OkHttpClient, authorId: Long): Author =
    makeRequest("$BASE_URL/api/authors/$authorId", client, object : TypeToken<Author>() {}) // slow не надо
