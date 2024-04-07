# README
This folder contains EC2 instance configurations needed to run Naksha-Hub or the PostgresQL database docker that is used by Naksha-Hub on EC2 instances.

## Install
To install a new EC2 Postgres instance the following steps are needed:

- Start a new EC2 instance of the type `r6idn.metal`.
  - Use **128 GiB** of **gp3** for root storage.
  - Locate the instance in **us-east-1** region.
  - Locate in `vpc-0c0b607d333227c5e` (_Direct Connect VPC E2E_).
  - Add into subnet `subnet-0a600ceb17a8e19fb` (_public E2E us-east-1a_0_).
  - Assign a public IP, so that you get access to docker registry.
  - Add security group `sg-0a39beed168bd9e97` (_wikvaya-e2e-us-east-1-sg_).
    - This allows all incoming traffic from `10.0.0.0/8`.
- Then create 16 EBS volumes using **gp3** type
  - Size: **1024 GiB**
  - Throughput: **1000 MiB/s**
  - IOPS: **16000**
  - Name: `naksha_postgresql_perftest_vNN` (with NN being **00** to **15**) 
  - Attach them to the instance.

Ones done, open up a shell to the machine and start the installation:

```bash
ssh ec2-user@IP

# Install necessary software
sudo yum -y install docker mdadm postgresql15 nc nmap fio nvme-cli

# Ensure that names are still what this document state.
lsblk

# Ensure that block-size is 4k
nvme id-ns -H /dev/nvme0n1 | grep LBA

# If not using 4k blocks format, change it (optimal would be 32K)
# Note, by default devices always use 512b formats!
nvme format --lbaf=1 /dev/nvme0n1

# Create temporary store
sudo mdadm --create --verbose --chunk=32 /dev/md0 --level=0 --name=pg_temp --raid-devices=4 /dev/nvme0n1 /dev/nvme1n1 /dev/nvme2n1 /dev/nvme3n1

# Create consistent store
sudo mdadm --create --verbose --chunk=32 /dev/md1 --level=0 --name=pg_data --raid-devices=16 /dev/nvme5n1 /dev/nvme6n1 /dev/nvme7n1 /dev/nvme8n1 /dev/nvme9n1 /dev/nvme10n1 /dev/nvme11n1 /dev/nvme12n1 /dev/nvme13n1 /dev/nvme14n1 /dev/nvme15n1 /dev/nvme16n1 /dev/nvme17n1 /dev/nvme18n1 /dev/nvme19n1 /dev/nvme20n1

# Review that the RAID is created
sudo cat /proc/mdstat

# Store the configuration
sudo mdadm --detail --scan --verbose | sudo tee -a /etc/mdadm.conf

# Create file systems
# -m = reserved space for root (we do not need this)
# -b = block-size, should always be the same as the MMU, so always 4k
# -C = chunk-size, bigalloc amount of byte to allocate in chunk
# -g = blocks-per-group, defaults to 32768 (128 MiB), with this setting all groups start at device 0 in the raid!
# -O Enable additional features, here bigalloc (chunk allocation) and no journal
# -E RAID parameters
#   stride: essentials the chunk-size (aka stride-size) of the raids (we want 32kb)
#   stripe-width: the product of number of disks and stride-size 
# In a nutshell: Either use an odd number of drives with corrsponding un-even stripe-width or change the group size to prevent that all groups start at disk #0!
# see: https://manpages.debian.org/experimental/e2fsprogs/mkfs.ext4.8.en.html
sudo mkfs.ext4 -m 0 -b 4096 -C 32768 -g 32760 -O bigalloc,^has_journal -E stride=32,stripe-width=128 /dev/md0
sudo mkfs.ext4 -m 0 -b 4096 -C 32768 -g 32760 -O bigalloc,^has_journal -E stride=32,stripe-width=512 /dev/md1

# Create postgres user
sudo groupadd postgres -g 5430
sudo useradd postgres -u 5432 -g 5430

# Mount the disks
sudo mkdir -p /mnt/pg_temp && sudo chown postgres:postgres /mnt/pg_temp
sudo mkdir -p /mnt/pg_data && sudo chown postgres:postgres /mnt/pg_data
sudo mount /dev/md0 /mnt/pg_temp 
sudo mount /dev/md1 /mnt/pg_data

# Remove lost and found folders (this prevents initdb)
sudo rm -rf /mnt/pg_temp/lost+found/ /mnt/pg_data/lost+found/

# Start the docker
sudo systemctl start docker
sudo docker pull hcr.data.here.com/naksha-devops/naksha-postgres:amd64-v16.2-r0
sudo docker run --name naksha_pg --privileged -v /mnt/pg_data:/usr/local/pgsql/data -v /mnt/pg_temp:/usr/local/pgsql/temp --network host -d hcr.data.here.com/naksha-devops/naksha-postgres:amd64-v16.2-r0
sudo docker logs naksha_pg
# Find the generated postgres root password, looks like:
# Initialized database with password: zFKRsAEWoJteGUCobBxgJmNrDLeJARNP
# Now update configuration
sudo docker stop naksha_pg
sudo vim /mnt/pg_data/postgresql.conf
# Jump to the end of the file, uncomment the line
# include_if_exists = '/home/postgres/r6idn.metal.conf'
# Then save and exit, restart docker
sudo docker start naksha_pg
```

## Management

### Docker
```bash
# Stop docker
sudo docker stop naksha_pg
# Remove docker (delete the container)
sudo docker rm naksha_pg
# Restart docker
sudo docker start naksha_pg
```

### Mounts
```bash
sudo umount /mnt/pg_data
sudo umount /mnt/pg_temp
```

### RAID
Requires that docker is shut down.
```bash
# Stop the raid
sudo mdadm --stop /dev/md1
sudo mdadm --stop /dev/md0
# Remove all drives from RAID
sudo mdadm --remove /dev/md{0|1}
# Remove failed drive
sudo mdadm --fail /dev/nvme{n}n1 --remove /dev/nvme{n}n1
# Zero superblock
sudo mdadm --zero-superblock /dev/nvme{n}n1
```

Fore more information see [How to remove software raid with mdadm](https://www.diskinternals.com/raid-recovery/how-to-remove-software-raid-with-mdadm/).

## Debugging
Test the connection:
```bash
psql "user=postgres sslmode=disable host=localhost dbname=unimap"
```

Review if the socket is bound:
```bash
sudo netstat -tulpn | grep :5432
sudo nmap localhost
```
