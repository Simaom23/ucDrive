# ucDrive
A plataforma ucDrive permite realizar todas as operações indispensáveis para
que os utilizadores possam armazenar de maneira simples os seus ficheiros num servidor remoto. 

Permite, então efetuar operações como: 
- Carregar um ficheiro
- Descarregar um ficheiro
- Listar ficheiros
- Mudar de diretórios

Como todo e qualquer software está sujeito a falhas, a plataforma ucDrive considera a possibilidade
de ocorrência de falhas e lida com estas de forma adequada através de mecanismos de
failover.

## Instalar o ucDrive:

Servidor Primário e Secundário:

- Para instalar o servidor deverão colocar os ficheiros que estão no interior da pasta serverClient numa pasta à vossa escolha;
- Para correr a aplicação servidor deverão usar o terminal com java -jar ucDrive.jar no diretório onde foi colocado o conteúdo.

Cliente:

- Para instalar o cliente deverão colocar os ficheiros que estão no interior da pasta driveClient numa pasta à vossa escolha;
- Para correr a aplicação cliente deverão usar o terminal com java -jar terminal.jar no diretório onde foi colocado o conteúdo.

### Ficheiro settings.properties:

Os endereços e portos dos servidores, primário e secundário, deverão ser colocados da seguinte forma:

- primary.server="primaryAddress"
- primary.port="primaryPort"
- secondary.server="secondaryAddress"
- secondary.port="secondaryPort"

Os nomes entre aspas deverão ser substituidos pelos respetivos endereços e portos, por exemplo, primary.server=0.0.0.0 e primary.port=6700.

### Ficheiro users.properties:

Cada utilizador deverá ser colocado no ficheiro da seguinta forma:

- "username".password="password"
- "username".cclife="cclife"
- "username".address="address"
- "username".cellphone="cellphone"
- "username".department="department"

Os nomes entre aspas deverão ser substituidos pelo pretendido, por exemplo bob.password=iambob.

### Ficheiro conf.properties:

Os endereços e portos dos servidores, primário e secundário, deverão ser colocados da seguinte forma:

- primary.server="primaryAddress"
- primary.port="primaryPort"
- primary.udp="primaryUdpPort"
- secondary.server="secondaryAddress"
- secondary.port="secondaryPort"
- secondary.udp="secondaryUdpPort"

Os nomes entre aspas deverão ser substituidos pelos respetivos endereços e portos, por exemplo, primary.server=0.0.0.0 e primary.port=6700.

## Utilização e comandos:

- **passwd** -> permite ao utilizador mudar a sua password
- **ls** -> permite listar os ficheiros da atual diretoria remota
- **cd** -> permite alterar a diretoria remota
  - **cd ..** -> Volta um diretorio atrás
  - **cd .** ou **cd /** ou **cd home** -> Volta ao diretorio home
  - É possivel percorrer mais que um diretorio de uma vez com **cd /diretorio1/diretorio2** ...
- **ls -p** -> permite listar os ficheiro da atual dirtoria local
- **cd -p** -> permite alterar a diretoria local
  - **cd ..** -> Volta um diretorio atrás
  - **cd .** ou **cd /** ou **cd home** -> Volta ao diretorio root local
  - É possivel percorrer mais que um diretorio de uma vez com cd /diretorio1/diretorio2 ...
- **put "nome do ficheiro"** ou **"directoria/nome do ficheiro"** -> permite fazer upload de um ficheiro da diretoria local no servidor remoto
- **get "nome do ficheiro"** ou **get "directoria/nome do ficheiro"** -> permite fazer download de um ficheiro ficheiro no servidor remoto para a diretoria local
- **server -primary** -> permite mudar o endereço e o porto do servidor primário
- **server -secondary** -> permite mudar o endereço e o porto do servidor secundário
- **exit** -> sai da aplicação
