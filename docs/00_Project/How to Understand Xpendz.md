# How to Understand Xpendz

**Fecha:** 15 de agosto de 2026  
**Versión:** 1.0

---

## If you have 10 minutes...

Read in this order:

1. [Project Overview](README.md)
2. [Master Architecture v1.0](../03_Architecture/Master%20Architecture%20v1.0.md)
3. [Glossary](Glossary.md)

You will understand the project's purpose, architecture, and key terms.

---

## If you have 30 minutes...

Read the 10-minute documents, then:

4. [Design System v2.0](../02_DesignSystem/Xpendz%20Design%20System%20v1.0.md)
5. [Token System Migration Blueprint](../05_Roadmaps/Token%20System%20Migration%20Blueprint%20v1.0.md)
6. [Module Dashboard](../03_Architecture/Module%20Dashboard.md)
7. [Module Loans](../03_Architecture/Module%20Loans.md)
8. [Module Backup](../03_Architecture/Module%20Backup.md)

You will understand the visual system, main modules, and key flows.

---

## If you are fixing a bug...

1. Identify the module: Dashboard, Transactions, Loans, etc.
2. Read the module's architecture document in `docs/03_Architecture/`.
3. Check `docs/01_Audits/` and `docs/07_Quality/` for related findings.
4. Review the ADRs in `docs/04_Decisions/` if the bug touches architecture.
5. Use the [Glossary](Glossary.md) to understand domain terms.

---

## If you are implementing a feature...

1. Read [Documentation Standards](Documentation%20Standards.md).
2. Identify affected modules in `docs/03_Architecture/`.
3. Review [Master Architecture v1.0](../03_Architecture/Master%20Architecture%20v1.0.md) for patterns.
4. Read relevant ADRs in `docs/04_Decisions/`.
5. Check [Design System v1.0](../02_DesignSystem/Xpendz%20Design%20System%20v1.0.md) if the feature changes UI.
6. After implementation, document the decision or update the module doc.

---

## If you are changing synchronization...

1. Read [Master Architecture v1.0](../03_Architecture/Master%20Architecture%20v1.0.md) → Synchronization Flow.
2. Read [ADR-005 Manual Pull-Based Sync](../04_Decisions/ADR-005%20Manual%20Pull-Based%20Sync.md).
3. Study the sync implementation in repositories and `SyncViewModel`.
4. Understand `DeviceIdProvider` and timestamp-based conflict resolution.

---

## If you are changing Room...

1. Read [Master Architecture v1.0](../03_Architecture/Master%20Architecture%20v1.0.md) → Database Architecture.
2. Read `app/src/main/java/com/myfinances/data/local/AppDatabase.kt`.
3. Review existing migrations before adding a new one.
4. Update relevant module documentation and the Master Architecture.

---

## If you are changing Compose UI...

1. Read [Design System v1.0](../02_DesignSystem/Xpendz%20Design%20System%20v1.0.md).
2. Read [Design Tokens Specification](../02_DesignSystem/Design%20Tokens%20Specification%20v1.0.md).
3. Read [ADR-004 Design Token System](../04_Decisions/ADR-004%20Design%20Token%20System.md).
4. Use `XpendzThemeTokens` instead of hardcoded values.
5. Document new reusable components if they become patterns.
