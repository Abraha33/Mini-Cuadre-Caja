package envaxsantander.abrahamcaceres.cuadre_caja_app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import envaxsantander.abrahamcaceres.cuadre_caja_app.data.AuthRepository
import envaxsantander.abrahamcaceres.cuadre_caja_app.data.authStateFlow
import envaxsantander.abrahamcaceres.cuadre_caja_app.domain.Result
import envaxsantander.abrahamcaceres.cuadre_caja_app.domain.UserSession
import envaxsantander.abrahamcaceres.cuadre_caja_app.domain.toUserMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AuthFormMode {
    Login,
    Register,
}

data class AuthFormState(
    val mode: AuthFormMode = AuthFormMode.Login,
    /** Incrementa solo tras fallos de login contra el servidor (no validación local). */
    val failedLoginAttempts: Int = 0,
    val nombre: String = "",
    val email: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val submitting: Boolean = false,
    val error: String? = null,
)

sealed class AuthSessionState {
    /** App start or processing auth + Firestore profile. */
    data object Checking : AuthSessionState()

    data class SignedOut(
        val message: String? = null,
    ) : AuthSessionState()

    data class Authenticated(
        val session: UserSession,
    ) : AuthSessionState()
}

class AuthViewModel(
    private val repo: AuthRepository = AuthRepository(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
) : ViewModel() {

    private val _session = MutableStateFlow<AuthSessionState>(AuthSessionState.Checking)
    val session: StateFlow<AuthSessionState> = _session.asStateFlow()

    private val _form = MutableStateFlow(AuthFormState())
    val form: StateFlow<AuthFormState> = _form.asStateFlow()

    init {
        viewModelScope.launch {
            auth.authStateFlow().collectLatest { user ->
                if (user == null) {
                    val msg = pendingSignedOutMessage
                    pendingSignedOutMessage = null
                    _session.value = AuthSessionState.SignedOut(message = msg)
                    return@collectLatest
                }
                _session.value = AuthSessionState.Checking
                when (val profile = repo.fetchUserSession(uid = user.uid, email = user.email)) {
                    is Result.Ok -> {
                        val s = profile.data
                        if (!s.activo) {
                            pendingSignedOutMessage = "Tu cuenta está desactivada."
                            repo.signOut()
                        } else {
                            _session.value = AuthSessionState.Authenticated(session = s)
                            _form.update {
                                it.copy(
                                    failedLoginAttempts = 0,
                                    mode = AuthFormMode.Login,
                                )
                            }
                        }
                    }
                    is Result.Err -> {
                        pendingSignedOutMessage = profile.error.toUserMessage()
                        repo.signOut()
                    }
                }
            }
        }
    }

    private var pendingSignedOutMessage: String? = null

    fun consumeSignedOutMessage() {
        val s = _session.value
        if (s is AuthSessionState.SignedOut && s.message != null) {
            _session.value = AuthSessionState.SignedOut(message = null)
        }
    }

    fun setMode(mode: AuthFormMode) {
        if (mode == AuthFormMode.Register && _form.value.failedLoginAttempts < 2) return
        _form.update {
            it.copy(
                mode = mode,
                error = null,
                confirmPassword = if (mode == AuthFormMode.Login) "" else it.confirmPassword,
            )
        }
    }

    fun onNombreChange(v: String) = _form.update { it.copy(nombre = v, error = null) }
    fun onEmailChange(v: String) = _form.update {
        it.copy(
            email = v,
            error = null,
            failedLoginAttempts = if (it.mode == AuthFormMode.Login) 0 else it.failedLoginAttempts,
        )
    }
    fun onPasswordChange(v: String) = _form.update { it.copy(password = v, error = null) }
    fun onConfirmPasswordChange(v: String) = _form.update { it.copy(confirmPassword = v, error = null) }

    fun submit() {
        val mode = _form.value.mode
        when (mode) {
            AuthFormMode.Login -> login()
            AuthFormMode.Register -> register()
        }
    }

    fun signOut() {
        viewModelScope.launch {
            repo.signOut()
        }
    }

    private fun login() {
        val email = _form.value.email.trim()
        val password = _form.value.password
        if (email.isBlank() || password.isBlank()) {
            _form.update { it.copy(error = "Correo y contraseña son obligatorios.") }
            return
        }

        viewModelScope.launch {
            _form.update { it.copy(submitting = true, error = null) }
            when (val r = repo.login(email, password)) {
                is Result.Ok -> {
                    _form.update { it.copy(failedLoginAttempts = 0) }
                }
                is Result.Err -> {
                    _form.update {
                        it.copy(
                            submitting = false,
                            error = r.error.toUserMessage(),
                            failedLoginAttempts = it.failedLoginAttempts + 1,
                        )
                    }
                }
            }
            if (_form.value.submitting) {
                _form.update { it.copy(submitting = false) }
            }
        }
    }

    private fun register() {
        val nombre = _form.value.nombre.trim()
        val email = _form.value.email.trim()
        val password = _form.value.password
        val confirm = _form.value.confirmPassword
        when {
            nombre.isBlank() -> {
                _form.update { it.copy(error = "El nombre es obligatorio.") }
                return
            }
            email.isBlank() || password.isBlank() -> {
                _form.update { it.copy(error = "Correo y contraseña son obligatorios.") }
                return
            }
            password != confirm -> {
                _form.update { it.copy(error = "Las contraseñas no coinciden.") }
                return
            }
            password.length < 6 -> {
                _form.update { it.copy(error = "La contraseña debe tener al menos 6 caracteres.") }
                return
            }
        }

        viewModelScope.launch {
            _form.update { it.copy(submitting = true, error = null) }
            when (val r = repo.register(email = email, password = password, nombre = nombre)) {
                is Result.Ok -> {
                    _form.update { it.copy(failedLoginAttempts = 0) }
                }
                is Result.Err -> {
                    _form.update {
                        it.copy(submitting = false, error = r.error.toUserMessage())
                    }
                }
            }
            if (_form.value.submitting) {
                _form.update { it.copy(submitting = false) }
            }
        }
    }
}
