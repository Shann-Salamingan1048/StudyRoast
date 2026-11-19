# Push StudyRoast to GitHub

Your terminal showed:

```text
On branch Main
nothing to commit, working tree clean
```

Git branch names are case-sensitive. Your local branch is `Main`, but you previously tried to push `main`.

## 1. Open the project folder

```bash
cd ~/Documents/AndroidProj/StudyApp
```

## 2. Rename the branch to `main`

```bash
git branch -m Main main
```

## 3. Verify the branch and existing commit

```bash
git branch --show-current
git log -1 --oneline
```

The branch should be `main`, and `git log` should show your latest commit. If `git log` says there are no commits, stop and create an initial commit first after reviewing `git status`.

## 4. Confirm the GitHub remote

```bash
git remote -v
```

Your `origin` should point to:

```text
https://github.com/Shann-Salamingan1048/StudyRoast.git
```

## 5. Push to GitHub

```bash
git push -u origin main
```

After the first successful push, future updates can normally use:

```bash
git add .
git status
git commit -m "Update StudyRoast"
git push
```

Review staged files before committing. Keep API keys, `local.properties`, build outputs, and large local `.gguf` model files out of Git. Be careful with the separately tracked `third_party/llama.cpp` repository: if Git warns about an embedded repository, address it as a submodule or another explicit dependency before committing.

Repository: https://github.com/Shann-Salamingan1048/StudyRoast
