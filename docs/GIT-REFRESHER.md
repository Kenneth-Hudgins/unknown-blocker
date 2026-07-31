# Git refresher (for this project)

You used Git in college; here’s the modern day-to-day loop for **Unknown Blocker**.

## Core idea

- **Working tree** = files on disk  
- **Staging area** (`git add`) = what will go into the next snapshot  
- **Commit** = a saved snapshot with a message  
- **Remote** (`origin` on GitHub) = backup + sharing copy in the cloud  
- **Branch** = a movable label pointing at a commit (we use `main`)

```
edit files → git add → git commit → git push
                ↑              ↓
           git status     git log
```

## Commands you’ll actually use

Run these from the project folder:

`C:\Users\Kenneth\Desktop\20-Projects\02-call-blocking-app-refined`

| Goal | Command |
|------|---------|
| What’s changed? | `git status` |
| See the diff | `git diff` (unstaged) / `git diff --staged` |
| Stage everything meaningful | `git add -A` |
| Stage one file | `git add path\to\file` |
| Save a snapshot | `git commit -m "short description"` |
| History | `git log --oneline -10` |
| Upload to GitHub | `git push` |
| Download updates | `git pull` |
| Undo unstaged edits to a file | `git checkout -- path\to\file` (or `git restore path`) |
| Unstage a file | `git restore --staged path` |

### Good commit messages

```
feat: show blocked numbers under toggle
fix: request call screening role on resume
docs: clarify SMS limitations in README
chore: bump versionCode to 2
```

Short subject line; optional body after a blank line.

## Branch workflow (when you want it)

```bash
git checkout -b feat/whitelist-numbers   # create + switch
# ... edit, commit ...
git push -u origin HEAD                 # publish branch
# open a PR on GitHub, merge, then:
git checkout main
git pull
```

For a solo app, committing straight to `main` is fine until you want PRs.

## What we deliberately do NOT commit

See `.gitignore`. Especially:

- `local.properties` (your machine’s Android SDK path)
- `.gradle/`, `build/`, `.idea/`
- keystores / APKs

## One-time machine setup (already partly done)

```bash
git config --global user.name "Your Name"
git config --global user.email "you@example.com"   # or GitHub noreply email
gh auth login                                      # browser login for GitHub CLI
```

This repo currently has **local** (repo-only) identity:

- name: Kenneth Hudgins  
- email: KennethHudgins9@users.noreply.github.com  

Change anytime:

```bash
git config user.email "your-real-or-noreply@email"
```

## GitHub CLI shortcuts

```bash
gh repo create unknown-blocker --public --source=. --remote=origin --push
gh repo view --web
gh status
```

## Recovering from “oh no”

| Situation | Fix |
|-----------|-----|
| Committed but not pushed; want to redo last commit message | `git commit --amend -m "better message"` |
| Committed wrong file; not pushed | `git reset --soft HEAD~1` (keeps changes staged) |
| Need a clean copy of last commit | `git restore .` (discards uncommitted work — careful) |
| Already pushed a bad commit | Prefer a new fixing commit; avoid rewriting history on `main` if others use it |

## Mental model vs college Git

Same fundamentals. What’s new in practice:

1. Default branch is usually **`main`**, not `master`
2. GitHub no longer accepts account **passwords** for `git push` — use **`gh auth login`** or a **Personal Access Token**
3. **`gh`** CLI creates repos/PRs without clicking around the website as much
4. Conventional commit prefixes (`feat:`, `fix:`) are a social convention, not required by Git

---

**This project’s happy path after a code change**

```bash
cd C:\Users\Kenneth\Desktop\20-Projects\02-call-blocking-app-refined
git status
git add -A
git commit -m "feat: describe the change"
git push
```
