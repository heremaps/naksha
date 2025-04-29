# Versioning (_draft_)
This document informs about the versioning of the Naksha project. Naksha uses semantic versioning.

External libraries like e.g. `lib-moderation` or `violation-extension` should have their own versioning and branching. It may be
synchronized to Naksha, but this is not necessary.

## Components
Naksha versions have the following components:

- Major: Major version, incremented on a breaking change.
- Minor: Minor version, incremented for new features, but without breaking changes.
- Revision: Revision, incremented for every new release with fixes and improvements.
- Pre-Release-Tag: An optional addition being "alpha" or "beta" to signal internal early releases.
- Pre-Release-Version: If a pre-release-tag is set, this signals the release number (0 to 254).

Note that a pre-release is before a release, so `3.0.0.alpha-0` is earlier than `3.0.0`.

## Text representation
The string representation of a Naksha version is: `{major}.{minor}.{release}[.{pre-release-tag}-{pre-release-version}]`

## Numerical representation
Naksha versions can be encoded into a 64-bit integer. The format is:

- Major:16 - The major version between 0 and 32767
- Minor:16 - The minor version between 0 and 65535
- Revision:16 - The revision between 0 and 65535
- Pre-Release-Tag:8 - The pre-release-tag
  - 0x01 - `alpha`
  - 0x02 - `beta`
  - 0xff - `final`
- Pre-Release-Version:8 - The pre-release version between 0 and 254, 255 for `final`.

This means, the lower 16 bit of the version are always fully set for releases (`0xffff`).

The numerical versions can be compared using normal integer operations.

## Branches vs Tags
A branch is a development tree, merging should happen upwards, but development can continue in multiple branches for some time. Fixes may be back-ported to lower branches.

When a branch is ready for release, it will be tagged according to its version, for example `2.0.13` or `2.1.0`. These tags are to be
checked out by CI/CD pipelines, to be build and eventually can be deployed to all environments. The tag will be placed upon the last commit that eventually made the corresponding branch ready for release. **All branches should always have green tests before being tagged for release**. If a release is broken, the tag should be deleted and this version becomes a whole in versioning, but we do not want to keep broken tags.

Pre-releases are not tagged and not deployed using normal CI/CD pipelines. They are only intended for internal testing and development. Every branch should stay in pre-release until ready for release. Then the pre-release tags shall be removed and a deployment should be done. Thereafter, the version is incremented and again becoming a pre-release until being ready for release.

## Versions

| Version | Branch             | Changes                                                                                  |
|---------|--------------------|------------------------------------------------------------------------------------------|
| 1.x.x   | master             | Naksha version 1, modified Data-Hub branch.                                              |
| 2.0.x   | Naksha_maintenance | New Naksha release.                                                                      |
| 3.0.x   | naksha_3_0         | Performance optimizations, incompatible change of codec. Currently in branch `alw_jbon`. |

