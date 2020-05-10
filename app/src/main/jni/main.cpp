/******************************************************************************

Welcome to GDB Online.
GDB online is an online compiler and debugger tool for C, C++, Python, PHP, Ruby,
C#, VB, Perl, Swift, Prolog, Javascript, Pascal, HTML, CSS, JS
Code, Compile, Run and Debug online from anywhere in world.

*******************************************************************************/
#include <iostream>

#include <thread>
#include <unistd.h> // sleep

#include <stdlib.h>

class Message {
public:
    int what;
    void * object;
    void * replyTo;
};

// A C program to demonstrate linked list based implementation of queue
// A linked list (LL) node to store a queue entry
struct QNode
{
    Message * message;
    struct QNode *next;
};

// The queue, front stores the front node of LL and rear stores ths
// last node of LL
struct Queue
{
    struct QNode *front, *rear;
};

// A utility function to create a new linked list node.
struct QNode* newNode(int16_t type);

// A utility function to create an empty queue
struct Queue *createQueue();

void storeMessage(struct Queue **qq, Message * message);

struct QNode * loadMessage(struct Queue **qq);

constexpr int REGISTER_CLIENT = 1;
constexpr int REGISTERED_CLIENT = 2;
constexpr int REGISTRATION_CONFIRMED = 3;

class Client;

class Server {
public:
    bool started = false;
    bool stopped = false;
    struct Queue * queue = nullptr;
    void process_message();
    void loop();
    std::thread * x = nullptr;
    void start();
    void post(Message * message);
    Message * get();
    void send(Client * c, int what);
    void send(Client * c, Message * message);
    void stop();
};

class Client {
public:
    bool started = false;
    bool stopped = false;
    struct Queue * queue = nullptr;
    void process_message();
    void loop();
    std::thread * x = nullptr;
    void start();
    void stop();
    void post(Message * message);
    Message * get();
    void send(Server * s, int what);
    void send(Server * s, Message * message);
    void registerToServer(Server * server);
};

void Server::process_message() {
    Message * message = get();
    if (message != nullptr) {
        std::cout << "SERVER: entered process_message with non nullptr message" << std::endl;
        std::cout << "SERVER: received message: " << message->what << std::endl;
        if (message->what == REGISTER_CLIENT) {
            std::cout << "SERVER: REGISTER_CLIENT" << std::endl;
            Message * message_ = new Message();
            message_->what = REGISTERED_CLIENT;
            std::cout << "SERVER: informing client of registration" << std::endl;
            send(static_cast<Client *>(message->replyTo), message_);
        } else if (message->what == REGISTRATION_CONFIRMED) {
            std::cout << "SERVER: client registration confirmed" << std::endl;
        }
        std::cout << "SERVER: exiting process_message" << std::endl;
    }
}

void Server::loop() {
    while(started == false);
    while(stopped == false) {
        process_message();
    };
}

void Server::start() {
    x = new std::thread([this]{ loop(); });
    started = true;
}

void Server::post(Message *message) {
    storeMessage(&queue, message);
}

Message *Server::get() {
    struct QNode * node = loadMessage(&queue);

    if (node == nullptr) return nullptr;
    return node->message;
}

void Server::send(Client *c, int what) {
    Message * message = new Message();
    message->what = what;
    message->replyTo = this;
    c->post(message);
}

void Server::send(Client *c, Message *message) {
    message->replyTo = this;
    c->post(message);
}

void Server::stop() {
    stopped = true;
    x->join();
    started = false;
    stopped = false;
    x = nullptr;
}

void Client::process_message() {
    Message * message = get();
    if (message != nullptr) {
        std::cout << "CLIENT: entered process_message with non nullptr message" << std::endl;
        std::cout << "CLIENT: received message: " << message->what << std::endl;
        if (message->what == REGISTERED_CLIENT) {
            std::cout << "CLIENT: informing server of registration" << std::endl;
            send(static_cast<Server *>(message->replyTo), REGISTRATION_CONFIRMED);
        }
        std::cout << "CLIENT: exiting process_message" << std::endl;
    }
}

void Client::loop() {
    while(started == false);
    while(stopped == false) {
        process_message();
    };
}

void Client::start() {
    x = new std::thread([this]{ loop(); });
    started = true;
}

void Client::stop() {
    stopped = true;
    x->join();
    started = false;
    stopped = false;
    x = nullptr;
}

void Client::post(Message *message) {
    storeMessage(&queue, message);
}

Message *Client::get() {
    struct QNode * node = loadMessage(&queue);

    if (node == nullptr) return nullptr;
    return node->message;
}

void Client::send(Server *s, int what) {
    Message * message = new Message();
    message->what = what;
    message->replyTo = this;
    s->post(message);
}

void Client::send(Server *s, Message *message) {
    message->replyTo = this;
    s->post(message);
}

void Client::registerToServer(Server *server) {
    send(server, REGISTER_CLIENT);
}


int main() {
    Message * message;

    Server s = Server();
    s.start();
    Client c = Client();
    c.start();

    std::cout << "registering client" << std::endl;
    c.registerToServer(&s);
    std::cout << "registered client" << std::endl;
    std::cout << "sleeping for 2 seconds" << std::endl;
    sleep(2);

    s.stop();
    c.stop();
}


struct QNode* newNode(Message * message)
{
    struct QNode *temp = (struct QNode*)malloc(sizeof(struct QNode));
    temp->message = message;
    temp->next = nullptr;
    return temp;
}

struct Queue *createQueue()
{
    struct Queue *q = (struct Queue*)malloc(sizeof(struct Queue));
    q->front = q->rear = nullptr;
    return q;
}

void storeMessage(struct Queue **qq, Message * message)
{
    if (*qq == nullptr)
        *qq = createQueue();

    // Create a new LL node
    struct QNode *temp = newNode(message);

    // If queue is empty, then new node is front and rear both
    if ((*qq)->rear == nullptr)
    {
        (*qq)->front = (*qq)->rear = temp;
        return;
    }

    // Add the new node at the end of queue and change rear
    temp->next = (*qq)->rear;
    (*qq)->rear = temp;
}

struct QNode * loadMessage(struct Queue **qq)
{
    if ((*qq) == nullptr)
        return nullptr;

    // If queue is empty, return NULL.
    if ((*qq)->rear == nullptr)
        return nullptr;

    // Store previous front and move front one node ahead
    struct QNode *temp = (*qq)->rear;
    (*qq)->rear = (*qq)->rear->next;

    // If front becomes nullptr, then change rear also as nullptr
    if ((*qq)->rear == nullptr)
        (*qq)->front = nullptr;
    return temp;
}


/*
is it possible to make a synchronous messaging system without requiring it to be asynchronous?

for example, a registration usually has 4 steps, 1. client requests server to register, 2. server registers client, 3. server requests client to confirm registration, 4, client requests server that registration is confirmed

in step 1, the client waits for the server to reply, in step 3, the server waits for the client to reply, in step 4, the client tries to send a request to the server but deadlocks due to step 3 as the server's main process loop is blocking due to waiting for the client to confirm its registration

would steps 3 and 4 need to be made asynchronous in order to avoid this deadlock
 */
