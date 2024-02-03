
sleep 60s
docker exec  $SERVERHOST /bin/sh -c " tail -f /var/log/supervisor/serverstart-stdout*"
 
 
docker cp bigtangle-mysql.sql bigtangle-mysql:/root
docker exec -it bigtangle-mysql bash
mysql -u root -ptest1234 < /root/bigtangle-mysql.sql

mkdir /var/lib/mysql/backup

mysqldump -u root -ptest1234 --databases info | gzip -c > /var/lib/mysql/$(date +"%Y-%b-%d")_info-backup.sql.gz


sudo  rsync -avz -e "ssh -i /home/cui/git/sshkeys/cui/id_rsa  "  \
  root@bigtangle.de:/data/vm/testprod-bigtangle-mysql/ \
 /data/vm/
  
  cd /var/lib/mysql/
  gzip -d  $(date +"%Y-%b-%d")_info-backup.sql.gz 
  docker cp $(date +"%Y-%b-%d")_info-backup.sql bigtangle-mysql:/root
 sudo mv $(date +"%Y-%b-%d")_info-backup.sql  /data/vm/bigtangle-mysql/var/lib/mysql/
  docker exec -it bigtangle-mysql bash
   mysql -u root -ptest1234 < /var/lib/mysql/$(date +"%Y-%b-%d")_info-backup.sql

git config user.name "cui"
git config user.email ""

https://github.com/BuilderIO/gpt-crawler

