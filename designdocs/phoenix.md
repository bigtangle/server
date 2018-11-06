
see more https://hub.docker.com/r/smizy/apache-phoenix/

docker run -it --rm --net vnet smizy/apache-phoenix:4.13.1-alpine bash

bin/sqlline-thin.py http://queryserver-1.vnet:8765

select * from BLOCKS;


!quit

