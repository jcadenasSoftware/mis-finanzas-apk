package com.jcadenas.xpendz.di

import com.jcadenas.xpendz.core.security.CipherPayloadCodec
import com.jcadenas.xpendz.core.security.EncryptionManager
import com.jcadenas.xpendz.core.security.EncryptionService
import com.jcadenas.xpendz.core.security.KeyStoreKeyProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Módulo de Hilt para componentes de seguridad.
 *
 * Provee:
 * - EncryptionService (interfaz)
 * - EncryptionManager (implementación concreta)
 * - KeyStoreKeyProvider (acceso a Android Keystore)
 * - CipherPayloadCodec (serialización de payloads)
 *
 * Separado de DatabaseModule y FirebaseModule para mantener
 * bajo acoplamiento y alta cohesión.
 */
@Module
@InstallIn(SingletonComponent::class)
object SecurityModule {

    /**
     * Provee el KeyStoreKeyProvider como singleton.
     *
     * Singleton porque el Keystore es un recurso a nivel de aplicación
     * y no necesitamos múltiples instancias.
     */
    @Provides
    @Singleton
    fun provideKeyStoreKeyProvider(): KeyStoreKeyProvider {
        return KeyStoreKeyProvider()
    }

    /**
     * Provee el CipherPayloadCodec como singleton.
     *
     * Stateless, por lo que singleton es óptimo para evitar
     * instanciación repetida.
     */
    @Provides
    @Singleton
    fun provideCipherPayloadCodec(): CipherPayloadCodec {
        return CipherPayloadCodec()
    }

    /**
     * Provee la implementación concreta de EncryptionService.
     *
     * Singleton porque:
     * - Requiere acceso a Keystore (costoso)
     * - No mantiene estado mutable
     * - Es seguro para uso concurrente
     */
    @Provides
    @Singleton
    fun provideEncryptionManager(
        keyProvider: KeyStoreKeyProvider,
        payloadCodec: CipherPayloadCodec
    ): EncryptionManager {
        return EncryptionManager(keyProvider, payloadCodec)
    }

    /**
     * Provee la interfaz EncryptionService.
     *
     * Los consumidores deben depender de la interfaz, no de la
     * implementación concreta, para:
     * - Desacoplamiento
     * - Testabilidad (fácil mocking)
     * - Compatibilidad futura con Desktop
     */
    @Provides
    @Singleton
    fun provideEncryptionService(
        encryptionManager: EncryptionManager
    ): EncryptionService {
        return encryptionManager
    }
}
