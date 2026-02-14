# Coding Style Guide

This document describes the Java-specific coding conventions for this project.

---

## Core Principles

These are the core principles that define how we write code.

### 1. TYPE INFERENCE - FORBIDDEN

**The `var` keyword is completely prohibited in Java.**

**Core Principle:** Code must be understandable without IDE assistance. The reader is more important than the writer.

**Always use explicit types:**
```java
// CORRECT - explicit type visible.
AudioManager manager = new AudioManager();
HashMap<String, Block> blockMap = new HashMap<>();
List<Player> players = new ArrayList<>();

// WRONG - hides the type.
var manager = new AudioManager();
var blockMap = new HashMap<String, Block>();
```

**Why?** Code must be understandable without IDE assistance. The reader is more important than the writer.

### 2. SINGLE-LINE CODE - NO WRAPPING

**Code must fit in the reader's working memory. If it does not fit on one line, it does not fit in the head either.**

Control flow, conditions, and signatures must stay on single lines.

```java
// GOOD - all parameters visible.
public void processData(String source, String target, boolean validate, int quality, ProcessingMode mode)
{
	// You can see everything. No hidden coupling.
}

// WRONG - wrapping hides complexity.
public void processData(
	String source,
	String target,
	boolean validate
)
{
	// Context is distributed vertically.
}

// CORRECT - condition visible.
if (condition1 && condition2 && condition3)
{
	doSomething();
}
```

**Why single-line?**
- **Visibility over abstraction** - You can see all parameters/conditions directly.
- Your brain has ~7±2 working memory slots. Single-line keeps everything in one frame.
- A long single line is honest. It shows the real complexity.

**Don't wrap. Don't hide. If it's long, it's long. That's the truth.**

### 3. ALLMAN BRACES - ALWAYS ON NEW LINE

Opening braces `{` ALWAYS go on a new line. No exceptions.

```java
// WRONG - K&R style.
if (condition) {
	doSomething();
}

// CORRECT - Allman style.
if (condition)
{
	doSomething();
}
```

**Why?** Visual symmetry makes code easier to scan and spot errors.

### 4. TABS FOR INDENTATION - NOT SPACES

Use tabs, period. Configure your editor properly.

Why? Because a single tab character is the true, unambiguous representation of a single indentation level. Spaces are a visual approximation; tabs are the logical unit.

### 5. COMPLETE SENTENCES IN COMMENTS

Comments start with capital letter, end with period.

```java
// WRONG.
// calculate average value

// CORRECT.
// Calculate the average value.
```

**Why?** Professional code looks professional. We're not writing text messages.

---

## Naming Conventions

Get the names right or the code gets rejected.

### Private Fields
Use **_lowerCamelCase starting with underscore**. Use `final` where possible:

```java
private final int _maxHealth;
private float _currentSpeed;
private final AudioManager _audioManager;
```

### Public Members and Methods
Use **lowerCamelCase** with no underscores:

```java
public void processData(String input)
{
	// Implementation.
}

public boolean validateInput(String data)
{
	// Implementation.
}
```

### Local Variables and Parameters
Use **lowerCamelCase** with no underscores. Use `final` for local variables where possible:

```java
public void calculateStatistics(byte[] data, int width, int height)
{
	final double averageValue = 0.0;
	final int totalPixels = width * height;
	
	for (int i = 0; i < data.length; i++)
	{
		averageValue += data[i];
	}
}
```

**Note:** Do NOT use `final` on parameters.

### Constants
Use **UPPER_CASE** with underscores:

```java
public static final int CHUNK_SIZE = 16;
public static final float GRAVITY = -9.81f;
private static final String GAME_TITLE = "SimpleCraft";
```

### Interfaces
**No `I` prefix.** Use **PascalCase**:

```java
public interface FileProcessor
{
	void process(String filePath);
	boolean validate(String data);
}
```

---

## Formatting Rules

### Indentation
- **Tabs only** - no spaces for indentation.
- One tab per level.
- Continuation lines get one additional tab.

### Braces Placement
Opening brace `{` on new line for:
- Classes, interfaces, enums
- Methods
- If/else blocks
- Loops (for, foreach, while, do-while)
- Switch statements
- Try/catch/finally blocks

**Examples:**

```java
public class FileProcessor
{
	private final String _filePath;
	
	public void process()
	{
		// Implementation.
	}
}

public enum ProcessingMode
{
	FAST,
	BALANCED,
	QUALITY
}

if (condition)
{
	// If body.
}

for (String file : files)
{
	// Loop body.
}
```

### Method Signatures
**All parameters on a single line:**

```java
public void processData(String source, String target, boolean validate, int quality, ProcessingMode mode)
{
	// Implementation.
}
```

If it's too long, you're doing too much - refactor it.

### Spacing Rules

**Between methods - one blank line:**
```java
public void methodOne()
{
	// Implementation.
}

public void methodTwo()
{
	// Implementation.
}
```

**Never more than one consecutive blank line anywhere.**

**No trailing spaces.**

---

## Control Structures

### If/Else Statements

**Always use braces** - even for single statements:
```java
// CORRECT.
if (health <= 0)
{
	respawnPlayer();
}

// WRONG - no braces.
if (health <= 0)
	respawnPlayer();
```

**Keep conditions on single line:**
```java
// CORRECT.
if (player.isAlive() && player.getHealth() > 0 && !player.isStunned())
{
	player.update(deltaTime);
}
```

**Include else only when both branches are meaningful** - early returns preferred for guard clauses:
```java
// CORRECT - early return.
if (!player.isAlive())
{
	return;
}

player.update(deltaTime);

// AVOID - unnecessary else.
if (player.isAlive())
{
	player.update(deltaTime);
}
else
{
	return;
}
```

### Switch Statements

**Use switch for 3+ cases, if/else for 1-2 cases.**

**Always include default case. Always include break.**

```java
// CORRECT - traditional switch.
String name;
switch (blockType)
{
	case GRASS:
	{
		name = "Grass";
		break;
	}
	case DIRT:
	{
		name = "Dirt";
		break;
	}
	default:
	{
		name = "Unknown";
		break;
	}
}

// WRONG - new switch expressions.
String name = switch (blockType)
{
	case GRASS -> "Grass";
	case DIRT -> "Dirt";
	default -> "Unknown";
};
```

**Only use braces when declaring variables inside a case.**

### Loops

Standard loop patterns:

```java
// For loop.
for (int i = 0; i < chunks.length; i++)
{
	chunks[i].rebuild();
}

// Enhanced for loop.
for (Enemy enemy : enemies)
{
	enemy.update(deltaTime);
}

// While loop.
while (running)
{
	processFrame();
}
```

---

## Language Features

### Avoid Streams API

**Do not use Java Streams API.** Use traditional loops instead.

Streams introduce hidden overhead, garbage collection pressure, and can cause memory leaks.

```java
// CORRECT - traditional loop.
final List<Enemy> alive = new ArrayList<>();
for (Enemy enemy : enemies)
{
	if (enemy.isAlive())
	{
		alive.add(enemy);
	}
}

// WRONG - streams.
List<Enemy> alive = enemies.stream()
	.filter(Enemy::isAlive)
	.collect(Collectors.toList());
```

**Why?** Visibility, performance, and memory control.

### No Null Annotations

Do not use `@NonNull`, `@Nullable`, or similar annotations. Use explicit null checks.

```java
// CORRECT - explicit null check.
if (player != null)
{
	player.update(tpf);
}

// WRONG - annotation magic.
public void update(@NonNull Player player)
{
	// ...
}
```

---

## Performance Considerations

### Garbage Collection Is Not Free

Avoid allocations in hot paths. Prefer object pooling or array reuse where possible.

```java
// AVOID - allocation in hot path.
for (int i = 0; i < 10000; i++)
{
	GameObject temp = new GameObject(); // GC pressure.
	processFrame(temp);
}

// PREFER - object pooling.
private final Queue<GameObject> _objectPool = new ArrayDeque<>();

for (int i = 0; i < 10000; i++)
{
	GameObject obj = !_objectPool.isEmpty() ? _objectPool.poll() : new GameObject();
	processFrame(obj);
	_objectPool.offer(obj); // Reuse.
}
```

### Use StringBuilder

```java
// Good - StringBuilder for multiple concatenations.
final StringBuilder sb = new StringBuilder();
for (int i = 0; i < 1000; i++)
{
	sb.append("Item ");
	sb.append(i);
}
final String result = sb.toString();
```

### Prefer Primitives Over Objects

Avoid wrapper objects when primitives suffice. Be mindful of autoboxing.

```java
// CORRECT - primitives.
int count = 0;
float speed = 1.5f;
boolean active = true;

// WRONG - unnecessary boxing.
Integer count = 0;
Float speed = 1.5f;
Boolean active = true;
```

---

## Anti-Patterns to Avoid

### ❌ Don't Use `var` Keyword
```java
// WRONG - hides type.
var manager = new AudioManager();

// CORRECT - explicit type.
AudioManager manager = new AudioManager();
```

### ❌ Don't Declare Multiple Variables on Same Line
```java
// WRONG.
int a, b = 0;

// CORRECT.
int a = 0;
int b = 0;
```

### ❌ Don't Over-Engineer - Avoid Single-Use Code
```java
// WRONG - constant used only once.
private static final int BUFFER_SIZE = 1024;
byte[] buffer = new byte[BUFFER_SIZE];

// CORRECT - inline it.
byte[] buffer = new byte[1024];
```

**Exception:** Create abstractions when used multiple times or likely to change.

### ❌ Don't Use Magic Numbers
```java
// WRONG.
if (status == 200) // What does 200 mean?
{
	// Process.
}

// CORRECT.
private static final int HTTP_STATUS_OK = 200;

if (status == HTTP_STATUS_OK)
{
	// Process.
}
```

### ❌ Don't Write Deeply Nested Code
```java
// WRONG - deeply nested.
if (condition1)
{
	if (condition2)
	{
		if (condition3)
		{
			// Too deep.
		}
	}
}

// CORRECT - early returns.
if (!condition1)
{
	return;
}

if (!condition2)
{
	return;
}

if (!condition3)
{
	return;
}

// Main logic at top level.
```

---

## Quick Reference Checklist

Before submitting code, verify:

- [ ] **No `var` keyword** - always use explicit types
- [ ] **No Streams API** - use traditional loops
- [ ] **No null annotations** - use explicit null checks
- [ ] **No new switch expressions** - use traditional switch only
- [ ] **GC awareness** - avoid allocations in hot paths, prefer pooling
- [ ] **Prefer primitives** - avoid boxing/unboxing
- [ ] **Single-line code** - all control flow, conditions, signatures on single lines
- [ ] **Allman braces** - opening `{` on new line
- [ ] **Tabs for indentation** - not spaces
- [ ] **Complete sentences in comments** - capital letter, period
- [ ] **_lowerCamelCase** for private fields (with underscore, use `final`)
- [ ] **lowerCamelCase** for public methods/local variables/parameters
- [ ] **UPPER_CASE** for constants
- [ ] **One blank line** between methods
- [ ] **Never more than one blank line** anywhere
- [ ] **No trailing spaces**
- [ ] **One variable per line** - no `int a, b;`
- [ ] **Switch for 3+ cases** - if/else for 1-2
- [ ] **No single-use constants/helpers**
- [ ] **Always use braces** on switch/if/else/loops
- [ ] **StringBuilder** for string concatenation

---

## Summary

Remember these core principles:

1. **No Type Inference** - Always use explicit types. `var` is forbidden.
2. **No Streams API** - Use traditional loops for visibility and performance.
3. **No Null Annotations** - Use explicit null checks.
4. **GC Awareness** - Avoid allocations in hot paths; prefer pooling.
5. **Primitive Preference** - Avoid boxing/unboxing overhead.
6. **Single-Line Code** - All control flow, conditions, signatures on single lines. Wrapping hides complexity instead of reducing it.
7. **Allman Braces** - Opening braces on new lines always.
8. **Complete Sentences** - Comments are documentation.
9. **Don't Over-Engineer** - YAGNI (You Ain't Gonna Need It).
10. **Traditional Switch Only** - No arrow syntax or switch expressions.
