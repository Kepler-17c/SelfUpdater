# Jardiff Format Specification

## General Properties

* The diff is stored in a ZIP file using the `.jardiff` extension.
* Some meta-data is added to the actual diff to ensure data integrity.
* Content of the file is split into two groups/directories:
    * `diff` contains the actual diff data.  
      Exact content depends on the diff version.
    * `meta` contains all meta information of the diff.  
      All versions provide at least the following files:
        * `diffHash` contains the SHA256 hex string of the diff file.
        * `newHash` contains the SHA256 hex string of the updated file.
        * `oldHash` contains the SHA256 hex string of the to be updated file.
        * `version` contains the version number as string.

With this the general file structure is:

```text
program-update.jardiff
 ├ diff
 │  └ ...
 └ meta
    ├ diffHash
    ├ newHash
    ├ oldHash
    └ version
```

## Version 1

### Description

By comparing the file trees, this type of diff identifies files that have changed.

| Pros                                               | Cons                                               |
|----------------------------------------------------|----------------------------------------------------|
| Simple, because it treats files as atomic objects. | Inefficient when many files contain small changes. |

### Additional Meta Data

| File Name      | Description                                                                                                                   |
|----------------|-------------------------------------------------------------------------------------------------------------------------------|
| `deletedFiles` | Contains a list of files, separated by `\n` characters, that have to be deleted.                                              |
| `movedFiles`   | Contains a list of move instructions, similar to `deletedFiles`: Lines alternate between "removed from" and "moved to" paths. |

### Diff Content

A subset of the updated file tree, only containing files that have been changed or were newly created.

Because files are viewed as atomic objects, a combination of moving and editing appears like two separate operations:
The file was deleted and a new file was created somewhere else.
