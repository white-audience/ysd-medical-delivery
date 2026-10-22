FROM nginx:1.27-alpine

COPY ./dist /usr/share/nginx/html
RUN chmod -R 755 /usr/share/nginx/html

CMD ["nginx", "-g", "daemon off;"]
