# Json Test Data
To download the test data, enter this directory and do the following:

First, log into AWS `cmtrd` account, and export default profile usage:

```bash
gimme-aws-creds --roles arn:aws:iam::326935872127:role/Here-USER
export AWS_PROFILE=cm-transition-rd-Here-USER
```

Then start the download:

```bash
aws s3api list-objects-v2 \
  --bucket sfw-baseline-seeding-sit2 \
  --prefix 04082025_1754290463/topology/ \
  --query "Contents[?Size > \`10485760\`].[Key, Size]" \
  | jq -r '.[] | .[0]' | xargs -n1 -P4 -I{} aws s3 cp "s3://sfw-baseline-seeding-sit2/{}" ./
```

This will download 219 files, with a size of around 3 GiB of compressed JSON, to unpack do this:

```bash
find . -type f -name '*.lz4' -exec sh -c 'lz4 -d --rm "$1" "${1%.lz4}"' _ {} \;
```

This unpacks all 219 files, resulting in 51 GiB of RAW JSON for testing.
