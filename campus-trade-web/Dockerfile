FROM nginx:1.28-alpine@sha256:a8b39bd9cf0f83869a2162827a0caf6137ddf759d50a171451b335cecc87d236
COPY dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/nginx.conf.template
EXPOSE 8080
USER nginx
ENTRYPOINT []
CMD ["sh", "-c", "case \"$TRUSTED_PROXY_CIDR\" in ''|'0.0.0.0/0'|'::/0') echo 'TRUSTED_PROXY_CIDR must be a restricted proxy network' >&2; exit 1;; esac; envsubst '$TRUSTED_PROXY_CIDR' < /etc/nginx/nginx.conf.template > /tmp/nginx.conf && exec nginx -c /tmp/nginx.conf -g 'daemon off;'"]
