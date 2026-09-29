# AGENTS

## Verificación
- Ejecutar tests unitarios Android: `./gradlew testDebugUnitTest`
- Ejecutar tests de migración/persistencia de Fase 2A: `./gradlew testDebugUnitTest --tests "com.jcadenas.xpendz.data.local.AppDatabaseMigrationTest" --tests "com.jcadenas.xpendz.data.repository.ObligationRepositoryTest"`

## Persistencia local
- Room `AppDatabase` está en versión 19.
- Las migraciones se registran en `app/src/main/java/com/myfinances/di/DatabaseModule.kt`.
