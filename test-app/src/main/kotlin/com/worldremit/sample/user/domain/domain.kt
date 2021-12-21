package com.worldremit.sample.user.domain

import java.util.UUID

data class UserId(val value: UUID)

data class Login(val value: String)

data class FirstName(val value: String)
data class MiddleName(val value: String)
data class LastName(val value: String)

data class Name(val first: FirstName, val middleName: MiddleName?, val lastName: LastName)

data class User(val id: UserId, val login: Login, val name: Name)
