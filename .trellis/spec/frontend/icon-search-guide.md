# Icon Search Guide

> How to find and use HugeIcons in this project via the `find-hugeicons` skill.

---

## Overview

RikkaHub uses **HugeIcons** (`me.rerere.hugeicons`) as its icon library. When you need to find an icon by concept or keyword, use the `/find-hugeicons` skill instead of guessing icon names.

---

## When to Use

- You need an icon for a UI element but don't know the exact name
- You want to verify an icon name before writing Compose code
- You want to see all available icons matching a concept (e.g., "search", "settings", "chat")

---

## Workflow

### Step 1: Search by Keyword

Use the `/find-hugeicons` skill, which searches the HugeIcons JAR in the Gradle cache:

```bash
# Find icons matching a keyword
jar -tf $(find ~/.gradle/caches -path "*hugeicons-compose*/jars/classes.jar" | head -1) \
  | grep -i "<keyword>" \
  | grep "stroke/.*Kt.class" \
  | sed 's|me/rerere/hugeicons/stroke/||;s|Kt.class||'
```

### Step 2: Use in Compose

```kotlin
// Import each icon explicitly
import me.rerere.hugeicons.stroke.ArrowLeft01

// Use with Icon composable
Icon(imageVector = HugeIcons.ArrowLeft01, contentDescription = null)
```

---

## Conventions

| Rule | Detail |
|------|--------|
| Package | All icons under `me.rerere.hugeicons.stroke` |
| Naming | PascalCase (e.g., `GlobalSearch`, `Settings03`, `AiMagic`) |
| Access | `HugeIcons.<IconName>` |
| Verification | **Always search first** — never guess icon names |
| Custom icons | Brand/special icons in `ui/components/ui/icons/` |

---

## Common Icons Reference

| Concept | Likely Names |
|---------|-------------|
| Back/navigation | `ArrowLeft01`, `ArrowRight01` |
| Search | `GlobalSearch`, `Search01` |
| Settings | `Settings01`, `Settings03` |
| AI/Magic | `AiMagic`, `AiBrain01` |
| Chat/Message | `Message01`, `BubbleChat` |

> These are examples — always verify via `/find-hugeicons` before use.

---

## Common Mistakes

| Mistake | Correct Approach |
|---------|-----------------|
| Guessing icon names | Use `/find-hugeicons` to search first |
| Using Lucide or Material icons | Use `HugeIcons` from `me.rerere.hugeicons` |
| Missing import | Each icon needs explicit import: `import me.rerere.hugeicons.stroke.<Name>` |
