# ADR-006: Cifrado de respaldos con PBKDF2 + AES-GCM

**Fecha:** 15 de agosto de 2026  
**Estado:** Aceptado  
**Decisores:** Equipo de arquitectura y seguridad Xpendz

---

## Contexto

Los usuarios pueden exportar un respaldo JSON de todos sus datos financieros. Era imperativo proteger este archivo contra acceso no autorizado si se almacena en almacenamiento externo o se comparte.

## Decisión

Cifrar los respaldos con:
- **PBKDF2WithHmacSHA256** para derivar una clave de 256 bits a partir de la contraseña del usuario.
- **AES-256-GCM** para cifrar el contenido JSON.
- **310,000 iteraciones** para derivación (OWASP 2021).
- **Salt e IV aleatorios** por respaldo.
- **Header versionado** con magic number y metadatos.

## Consecuencias

- **Positivas:**
  - Respaldo portátil entre Android y Desktop.
  - Seguridad independiente del dispositivo.
  - Formato abierto y estándar.

- **Negativas:**
  - El usuario debe recordar la contraseña de respaldo.
  - No hay recuperación si se olvida la contraseña.

## Alternativas consideradas

- **Cifrado con Android Keystore:** Rechazado porque rompe portabilidad entre dispositivos.
- **Zip con contraseña:** Rechazado por debilidad criptográfica.
