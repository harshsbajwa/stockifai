# Stage 1: Build the React application
FROM node:slim AS builder

# PNPM Configuration
ENV PNPM_HOME="/pnpm"
ENV PATH="$PNPM_HOME:$PATH"
RUN corepack enable

# Set the working directory for the build stage
WORKDIR /app

# Copy files
COPY ./package.json ./pnpm-lock.yaml* ./
COPY ./public ./public/
COPY ./src ./src/
COPY ./tsconfig.json ./
COPY ./postcss.config.js ./
COPY ./tailwind.config.js ./

# Install dependencies
RUN --mount=type=cache,id=pnpm,target=/pnpm/store pnpm install --frozen-lockfile

# Build the application
RUN pnpm run build

# Stage 2: Serve the application with Nginx
FROM nginx:alpine

# Copy the build output
COPY --from=builder /app/build /usr/share/nginx/html

# Copy custom nginx configuration.
COPY ./nginx.conf /etc/nginx/conf.d/default.conf

# Expose port 80
EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]