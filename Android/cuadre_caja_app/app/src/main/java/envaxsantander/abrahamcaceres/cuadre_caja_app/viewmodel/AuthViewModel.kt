package envaxsantander.abrahamcaceres.cuadre_caja_app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import envaxsantander.abrahamcaceres.cuadre_caja_app.data.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AuthUiState(
    val nombre: String = "",
    val email: String = "",
    val password: String = "",
    val loading: Boolean = false,
    val error: String? = null,
)

class AuthViewModel(
    private val repo: AuthRepository = AuthRepository(),
) : ViewModel() {
    private val _ui = MutableStateFlow(AuthUiState())
    val ui = _ui.asStateFlow()

    fun onNombreChange(v: String) = _ui.update { it.copy(nombre = v, error = null) }
    fun onEmailChange(v: String) = _ui.update { it.copy(email = v, error = null) }
    fun onPasswordChange(v: String) = _ui.update { it.copy(password = v, error = null) }

    fun login(onSuccess: () -> Unit) {
        val email = _ui.value.email.trim()
        val password = _ui.value.password
        if (email.isBlank() || password.isBlank()) {
            _ui.update { it.copy(error = "Email y password son obligatorios.") }
            return
        }

        viewModelScope.launch {
            _ui.update { it.copy(loading = true, error = null) }
            try {
                repo.login(email, password)
                onSuccess()
            } catch (t: Throwable) {
                _ui.update { it.copy(error = t.message ?: t.javaClass.simpleName) }
            } finally {
                _ui.update { it.copy(loading = false) }
            }
        }
    }

    fun register(onSuccess: () -> Unit) {
        val nombre = _ui.value.nombre.trim()
        val email = _ui.value.email.trim()
        val password = _ui.value.password
        if (nombre.isBlank() || email.isBlank() || password.isBlank()) {
            _ui.update { it.copy(error = "Nombre, email y password son obligatorios.") }
            return
        }

        viewModelScope.launch {
            _ui.update { it.copy(loading = true, error = null) }
            try {
                // TEMP: primer usuario admin para desbloquear pruebas.
                // Luego lo cambiamos a rol real desde consola / función.
                repo.register(email = email, password = password, nombre = nombre, rol = "admin")
                onSuccess()
            } catch (t: Throwable) {
                _ui.update { it.copy(error = t.message ?: t.javaClass.simpleName) }
            } finally {
                _ui.update { it.copy(loading = false) }
            }
        }
    }
}
