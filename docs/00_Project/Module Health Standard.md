# Module Health Standard v1.0

**Fecha:** 15 de agosto de 2026  
**Versión:** 1.0

---

## Purpose

Define a reusable standard to evaluate and document the health of every Xpendz module.

---

## Health Dimensions

| Dimension | Score Range | Description |
|-----------|-------------|-------------|
| **Architecture Quality** | 1-5 | How well the module follows project patterns (MVVM, Repository, DI, etc.) |
| **Documentation Quality** | 1-5 | How complete and current the module documentation is |
| **Complexity** | 1-5 | 1 = simple, 5 = highly complex |
| **Test Coverage** | 1-5 | Estimated coverage; 1 = none, 5 = comprehensive |
| **Technical Debt** | 1-5 | 1 = clean, 5 = significant debt |
| **Risk Level** | Low/Med/High | Risk of introducing bugs when modifying the module |
| **Refactor Recommended** | Yes/No | Whether a refactor should be planned |

---

## Scoring Criteria

### Architecture Quality

| Score | Meaning |
|-------|---------|
| 5 | Perfect fit with patterns, clear boundaries |
| 4 | Good fit, minor deviations |
| 3 | Adequate, some inconsistencies |
| 2 | Significant deviations from patterns |
| 1 | Difficult to understand or maintain |

### Documentation Quality

| Score | Meaning |
|-------|---------|
| 5 | Documented with all required sections and diagrams |
| 4 | Mostly documented, few gaps |
| 3 | Basic documentation exists |
| 2 | Minimal or outdated documentation |
| 1 | No documentation |

### Complexity

| Score | Meaning |
|-------|---------|
| 1 | Few classes, simple logic |
| 2 | Clear but with some interactions |
| 3 | Multiple entities and flows |
| 4 | Complex domain logic |
| 5 | Highly coupled, many edge cases |

### Test Coverage

| Score | Meaning |
|-------|---------|
| 1 | No tests |
| 2 | Few tests |
| 3 | Basic coverage |
| 4 | Good coverage |
| 5 | Comprehensive unit + integration tests |

### Technical Debt

| Score | Meaning |
|-------|---------|
| 1 | Clean, no known debt |
| 2 | Minor cosmetic debt |
| 3 | Some known issues |
| 4 | Several important issues |
| 5 | Critical debt blocking evolution |

---

## Module Health Template

```markdown
## Module Health

| Dimension | Score |
|-----------|-------|
| Architecture Quality | 4/5 |
| Documentation Quality | 3/5 |
| Complexity | 4/5 |
| Test Coverage | 1/5 |
| Technical Debt | 3/5 |
| Risk Level | Med |
| Refactor Recommended | Yes |
| Last Reviewed | 2026-08-15 |
| Next Review | 2026-11-15 |
```

---

## How to Use

Add this section at the end of every module architecture document. Update it during quarterly documentation reviews or after significant changes.
