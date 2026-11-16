# Key pair для доступа к instance (AWS EC2)

## Создание key pair

1. Разработчик открывает в консоли AWS раздел **EC2** -> **Key Pairs** и нажимает **Create key pair**.
2. Разработчик задаёт **Name** `zhsr-demo`, **Key pair type** **ED25519**, **Private key file format** **.pem** и
   нажимает **Create key pair**. Браузер скачивает private key - файл `zhsr-demo.pem`. AWS отдаёт private key только
   один раз.
3. При запуске instance (**Launch instance**) разработчик выбирает key pair `zhsr-demo` в поле **Key pair (login)**.

AWS записывает public key из key pair в instance только при запуске instance. Если instance запущен с другим key pair, разработчик подключается к instance через **Connect** -> **EC2 Instance Connect** и
дописывает public key нового key pair в файл `~/.ssh/authorized_keys`. Public key выводит команда
`ssh-keygen -y -f zhsr-demo.pem`.

## Подключение по ssh

Разработчик переносит private key в `~/.ssh` и закрывает файл от других пользователей:

```
mv ~/Downloads/zhsr-demo.pem ~/.ssh/
chmod 400 ~/.ssh/zhsr-demo.pem
```

Затем разработчик записывает в `.env` проекта пользователя и **Public IPv4 DNS** (или **Public IPv4 address**)
instance и путь к private key. Пользователь зависит от AMI: у Ubuntu - `ubuntu`, у Amazon Linux - `ec2-user`.

```
DEPLOY_HOST=ubuntu@<Public IPv4 DNS>
DEPLOY_SSH_KEY=~/.ssh/zhsr-demo.pem
```

Команда `ssh -i ~/.ssh/zhsr-demo.pem ubuntu@<Public IPv4 DNS>` проверяет, что key pair подходит.

## Постоянный адрес

После остановки instance AWS выдаёт instance новый **Public IPv4 address** и новый **Public IPv4 DNS**. Чтобы адрес
не менялся, разработчик выделяет **Elastic IP** (**EC2** -> **Elastic IPs** -> **Allocate Elastic IP address**) и
связывает его с instance (**Actions** -> **Associate Elastic IP address**). Без Elastic IP разработчик после каждой
остановки instance исправляет `DEPLOY_HOST` в `.env`.

Разработчик никому не передаёт private key и не кладёт его в git.
