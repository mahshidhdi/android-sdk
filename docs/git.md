# Git

We use Git for version control. This section explains the conventions we follow for git branches and messages.


## Branches
The git repository consists of two main branches

- **develop**: The code from different feature branches get merged into the `develop` branch
- **master**: The `master` should contain stable code. The `develop` branch will be merged into `master` once it has been sufficiently tested.


You should create separate feature branches when working on developing different features. The branch name should be in the following format:

```
<module>/<issue>-<description>
```

For example,

```
core/TR43-error-reporting
```

If the branch is related to an issue, the `<issue>` should contain the issue number. Prefix the issue number with two letters identying where the issue was reported (e.g, `TR` for trello, `GH` for github, etc.).

The `<description>` should be a few words describing the issue. Use hyphens to separate words.


## Commit Messages

Commit messages should follow the following syntax:

```
<type>(<module>): <message>
```

For example:
```
feat(notification): implement awesome feature
```

The `<type>` can be one of the following:
    
- **feat** (new feature)
- **fix** (bug fix)
- **docs** (changes to the documentation)
- **style** (formatting, etc; no production code change)
- **refactor** (refactoring code, e.g. renaming a variable)
- **test** (adding missing tests, refactoring tests; no production code change)
- **wip** (work in progress, e.g, working on a feature but not complete yet)

The `<module>` should be the name of the module which the commit is working on:

- core
- admin
- notification
- analytics
- ...


## Sample Workflow

Let's suppose we are trying to resolve this issue from Trello:

```
    [#55] Check for duplicate notifications and skip message if it is a duplicate.
```

First checkout into `develop` and make sure the branch is up-to-date

```
git checkout develop
git pull origin develop
```

Create a new branch for the issue
```
git checkout -b 'notification/TR55-check-duplicate'
```

We will then proceed to implement the new feature and make commits in the branch

```
git commit -m "feat(notification): check and skip duplicate notifications"
```

Next, we will push our branch to the remote repo

```
git push -u origin notification/TR55-check-duplicate
```

From the GitLab panel we will create a merge request and ask for our branch to be merged into `develop`. 

The code will then be reviewed. The reviewer may leave comments for us to fix and may make commits into the branch. Once the code has been reviewed we will need to resolve the comments. 

First, we will pull the branch to retrieve any commits which the reviewer has made

```
git pull origin notification/TR55-check-duplicate
```

The we will make our changes and push again

```
git commit -m "refactor(notification): separate duplicate checker into it's own class"
git commit -m "test(notification): test skipping duplicate notifications"

git push origin notification/TR55-check-duplicate
```

The reviewer will reveiw the changes. If any problems remain, we will go through this process again. If not, the code will be merged into the `develop` branch.