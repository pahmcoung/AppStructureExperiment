package com.example.myapplication.viewmodel

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import com.example.myapplication.domain.IDataRepository
import com.example.myapplication.domain.RequestResult
import com.example.myapplication.models.User
import com.example.myapplication.validators.EmailValidator
import com.example.myapplication.validators.PasswordValidator
import kotlinx.coroutines.*

sealed class LoginFormEvent {
    data class LoginFormChanged(
        val email: String,
        val password: String,
    ): LoginFormEvent()

    object LoginFormSubmit: LoginFormEvent()
}

data class LoginFormState(
    val email: String = "",
    val emailError: String? = null,
    val password: String = "",
    val passwordError: String? = null,
)

data class LoginFormSubmittedState(
    val isSuccessful: Boolean = false,
    val user: User? = null,
)

abstract class ILoginPageViewModel: IViewModel() {
    abstract val loginFormState: MutableState<LoginFormState>
    abstract val loginFormSubmittedState: MutableState<LoginFormSubmittedState>
    abstract fun onEvent(event: LoginFormEvent)
}

class LoginPageViewModel(
    private val dataRepository: IDataRepository,
    private val emailValidator: EmailValidator = EmailValidator(),
    private val passwordValidator: PasswordValidator = PasswordValidator(),
): ILoginPageViewModel() {

    override val loginFormState = mutableStateOf(LoginFormState())
    override val loginFormSubmittedState = mutableStateOf(LoginFormSubmittedState())

    private var loginJob: Job? = null
    private val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default

    override fun onEvent(event: LoginFormEvent) {
        when(event) {
            is LoginFormEvent.LoginFormChanged -> {
                loginFormState.value = LoginFormState(
                    email = event.email,
                    password = event.password,
                )
            }
            is LoginFormEvent.LoginFormSubmit -> {
                submitData()
            }
        }
    }

    private fun submitData() {
        val emailValidationResult = emailValidator.execute(loginFormState.value.email)
        val passwordValidationResult = passwordValidator.execute(loginFormState.value.password)

        val hasError = listOf(
            emailValidationResult,
            passwordValidationResult,
        ).any {validationResult->
            validationResult.errorMessage != null
        }

        if(hasError) {
            loginFormState.value = loginFormState.value.copy(
                emailError = emailValidationResult.errorMessage,
                passwordError = passwordValidationResult.errorMessage,
            )

            return
        }

        loginJob = CoroutineScope(defaultDispatcher).launch {
            when(val result = dataRepository.authenticate(
                email = loginFormState.value.email,
                password = loginFormState.value.password
            )){
                is RequestResult.OnSuccess -> {
                    result.data?.let {
                        loginFormSubmittedState.value = LoginFormSubmittedState(
                            isSuccessful = true,
                            user = it
                        )
                    }

                }
                is RequestResult.OnError -> {
                    loginFormSubmittedState.value = LoginFormSubmittedState(
                        isSuccessful = false,
                    )
                }
            }
        }
    }

    override fun dispose() {
        super.dispose()

        loginJob?.cancel()
        loginJob = null
    }
}