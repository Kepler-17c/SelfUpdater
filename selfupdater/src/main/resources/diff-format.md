# Jardiff Format Specification

## General Properties

* The diff is stored in a ZIP file using the `.jardiff` extension.
* Some meta-data is added to the actual diff to ensure data integrity.
* Content of the file is split into two groups/directories:
    * `data` contains the actual diff data.  
      Exact content depends on the diff version.
    * `meta` contains all meta information of the diff.  
      All versions provide the following files:
        * `diffHash` contains the SHA256 hex string of the diff file.
        * `newHash` contains the SHA256 hex string of the updated file.
        * `oldHash` contains the SHA256 hex string of the to be updated file.
        * `version` contains the version number as string.

With this the general file structure is:

```text
program-update.jardiff
 ├ data
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

### Diff Data

#### Structure

```text
program-update.jardiff
 ├ data
 │  ├ deletedFiles
 │  ├ movedFiles
 │  └ tree
 │     └ ...
 └ meta
    └ ...
```

Two files `deletedFiles` and `movedFiles` provide meta information, while `tree` contains the added or changed files:

| File Name      | Description                                                                                                                   |
|----------------|-------------------------------------------------------------------------------------------------------------------------------|
| `deletedFiles` | Contains a list of files, separated by `\n` characters, that have to be deleted.                                              |
| `movedFiles`   | Contains a list of move instructions, similar to `deletedFiles`: Lines alternate between "removed from" and "moved to" paths. |

Above-mentioned tree is a subset of the updated file tree, only containing files that have been changed or were newly
created.
Moved files do not appear here, but are required to be copied from the old jar when the new jar is being compiled.

To simplify the structure, moved files' source locations are listed in `deletedFiles`.
This keeps deletion instructions in one place, instead of spreading it over multiple files.

Combinations of moving and editing appear like two separate operations, because files are viewed as atomic objects:
The original file was deleted and a new file was created somewhere else.
