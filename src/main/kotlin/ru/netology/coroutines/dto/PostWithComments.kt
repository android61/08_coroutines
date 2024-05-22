package ru.netology.coroutines.dto

data class PostWithAuthorAndComments(
    val author: Author,
    val post: Post,
    val comments: List<Comment>
)

