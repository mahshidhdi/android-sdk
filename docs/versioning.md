# Versioning

We follow the [semantic versioning](https://semver.org/) scheme.

The version for all modules should remain the same. Whenever a new release is made, all module's versions should be updated together (even if a module is changed it's version should be updated).

### Version Code
The version code is derived from the version name using the scheme below:

```
  Mmmmppprr
```

Each character in the scheme represents a digit. The different characters relate to the different parts of the version name:

```
  M: Major
  m: Minor
  p: Patch
  r: Release Stage   01-50 -> alpha
                     51-98 -> beta
                     99    -> stable release
```


Some sample versions and their codes are shown below:

```
1.0.0-alpha1   -> 100000001

2.6.14-alpha12 -> 200601412

2.6.14         -> 200601499


```


