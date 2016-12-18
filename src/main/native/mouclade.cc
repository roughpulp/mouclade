extern "C" {
#include <errno.h>
#include <string.h>
#include <unistd.h>
#include <fcntl.h>
#include <pty.h>
#include <stdlib.h>
#include <signal.h>
#include <poll.h>
#include <sys/ioctl.h>
}

#include <iostream>
#include <string>
#include <memory>
#include <vector>
#include <boost/program_options.hpp>
#include <boost/filesystem.hpp>

using namespace std;
namespace program_opts = boost::program_options;
namespace fs = boost::filesystem;

namespace rpulp { namespace mouclade {

const string throw_errno_failure(const string& msg) {
    int err = errno;
    char error_msg[129];
    error_msg[0] = 0;
    strerror_r(err, &error_msg[0], 128);
    error_msg[128] = 0;
    throw std::runtime_error(msg + ", errno: " + to_string(err) + ", " + string(error_msg));
    return "";
}

class FileDesc {
public:

    static void set_nonblocking(int fd) {
        int opts = fcntl(fd, F_GETFL);
        if (opts == -1) {
            throw throw_errno_failure("fcntl get error");
        }
        opts = (opts | O_NONBLOCK);
        if (fcntl(fd, F_SETFL, opts) == -1) {
            throw throw_errno_failure("fcntl set error");
        }
    }

    FileDesc(int fd) : fd_(fd) {}
    
    ~FileDesc() {
        ::close(fd_);
    }
    
    int fd() const { return fd_;}
    
private:
	FileDesc(const FileDesc& that) = delete;
	FileDesc& operator=(const FileDesc& that) = delete;
    
    const int fd_;
};

class Buffer {
public:
    Buffer(int capacity): capacity_(capacity), position_(0), limit_(capacity) {
        bytes_ = new char[capacity_];
    }
    
    ~Buffer() {
        delete[] bytes_;
    }
    
    int position() const { return position_;}
    int limit() const { return limit_; }
    int capacity() const { return capacity_;}
    int remaining() const { return limit_ - position_; }
    
    Buffer& clear() {
        position_ = 0;
        limit_ = capacity_;
        return *this;
    }
    
    Buffer& flip() {
        limit_ = position_;
        position_ = 0;
        return *this;
    }
     
    int read(int fd) {
        int remaining = this->remaining();
        if (remaining <= 0) {
            throw std::runtime_error("buffer full, cannot read more data");
        }
        int rcnt = ::read(fd, &bytes_[position_], remaining);
        if (rcnt == -1) {
            throw throw_errno_failure("read error");
        }
        position_ += rcnt;
        return rcnt;
    }

    bool write(int fd) {
       int remaining = this->remaining();
        if (remaining <= 0) {
            return false;
        }
        int wcnt = ::write(fd, &bytes_[position_], remaining);
        if (wcnt == -1) {
            throw throw_errno_failure("write error");
        }
        position_ += wcnt;
        return wcnt;
    }

    char operator[](std::size_t idx) const {
        if (idx > capacity_) {
            throw std::runtime_error("buffer out of bounds, idx: " + std::to_string(idx) + " capacity: " + std::to_string(capacity_));
        }
        return bytes_[idx];
    }
    
private:
    Buffer(const Buffer& that) = delete;
	Buffer& operator=(const Buffer& that) = delete;
    
    const int capacity_;
    int position_;
    int limit_;
    char* bytes_;
};

typedef struct pollfd t_pollfd;

class PolledPipe {
public:
    PolledPipe(t_pollfd& in, t_pollfd& out, int capacity): in_(in), out_(out), read_mode_(true), buffer_(capacity) {}
    
    bool can_read() const { return read_mode_; }
    
    bool can_write() const { return ! read_mode_; }

    void prepare_polling() {
        if (can_read()) {
            in_.events |= POLLIN;
        }
        if (can_write()) {
            out_.events |= POLLOUT;
        }
    }
    
    void handle_io() {
        if (can_read()) {
            handle_read();
        }
        if (can_write()) {
            handle_write();
        }
    }

private:
	PolledPipe(const PolledPipe& that) = delete;
	PolledPipe& operator=(const PolledPipe& that) = delete;

    static void check_io_error(short revents) {
        short mask = ~(POLLIN | POLLOUT);
        if (revents & mask) {
            throw runtime_error("io error");
        }
    }

    void handle_read() {
        check_io_error(in_.revents);
        if ((in_.revents & POLLIN) && (buffer_.remaining() > 0)) {
            int before = buffer_.remaining();
            buffer_.read(in_.fd);
            if (buffer_.remaining() == before) {
                throw runtime_error("POLLIN flag present but read nothing"); // protect against endless loop
            }
            if (buffer_.position() > 0) {
                buffer_.flip();
                set_write_mode();
            }
        }
    }

    void handle_write() {
        check_io_error(out_.revents);
        if ((out_.revents & POLLOUT) && (buffer_.remaining() > 0)) {
            int before = buffer_.remaining();
            buffer_.write(out_.fd);
            if (buffer_.remaining() == before) {
                throw runtime_error("POLLOUT flag present but wrote nothing"); // protect against endless loop
            }
            if (buffer_.remaining() == 0) {
                buffer_.clear();
                set_read_mode();
            }
        }
    }
    
    void set_read_mode() { read_mode_ = true; }
    
    void set_write_mode() { read_mode_ = false; }
    
    t_pollfd& in_;
    t_pollfd& out_;
    bool read_mode_;    // true: read, false: write
    Buffer buffer_;
};

void set_window_size(int fd, int rows, int cols) {
    struct winsize wsize;
    wsize.ws_row = rows;
    wsize.ws_col = cols;
    wsize.ws_xpixel = 0;
    wsize.ws_ypixel = 0;
    if (::ioctl(fd, TIOCSWINSZ, &wsize) == -1) {
        throw throw_errno_failure("ioctl(TIOCSWINSZ) failed");
    }
}


class WindowSize {
public:
    WindowSize(int rows, int cols): rows_(rows), cols_(cols) {}
	WindowSize(const WindowSize& that): rows_(that.rows_), cols_(that.cols_) {}
	WindowSize& operator=(const WindowSize& that) {
		this->rows_ = that.rows_;
		this->cols_ = that.cols_;
		return *this;
	}

    int rows_;
    int cols_;
};

class Args {
public:
    Args(): termenv_("xterm"), winsize_(100, 80) {}

    string termenv_;
    WindowSize winsize_;
};


class Pty {
public:
    static unique_ptr<Pty> open(Args& args) {
        int master_fd;
        int pid = forkpty(&master_fd, nullptr, nullptr, nullptr);
        switch(pid) {
            case -1:
                throw throw_errno_failure("forkpty");
            case 0:
                exec_shell(args);
                break;
        }
        return make_unique<Pty>(pid, master_fd);
    }

    Pty(int child_pid, int master_fd) : child_pid_(child_pid), master_(master_fd) {}

    int master_fd() const { return master_.fd(); }
    
private:
	Pty(const Pty& that) = delete;
	Pty& operator=(const Pty& that) = delete;

    static void exec_shell(Args& args) {
        auto term = "TERM=" + args.termenv_;
        //
        vector<const char*> argv;
        argv.push_back("/bin/bash");
        argv.push_back("-i");
        argv.push_back(nullptr);
        //
        vector<const char*> envs;
        envs.push_back(term.c_str());
        envs.push_back(term.c_str());
        envs.push_back(nullptr);
        //
        //FIXME: dirty cast
        execve(argv[0], (char* const*)(&argv[0]), (char* const*)(&envs[0]));
    }
        
    const int child_pid_;
    const FileDesc master_;
};

namespace ut {

fs::path prepare_temp_dir() {
    fs::path temp_dir("./utests.tempdir");
    fs::remove_all(temp_dir);
    fs::create_directories(temp_dir);
    return temp_dir;
}

void assertTrue(bool value, const std::string msg) {
    if (! value) {
        throw msg;
    }
}

void assertFalse(bool value, const std::string msg) {
    if (value) {
        throw msg;
    }
}

void assertEquals(int expected, int actual, const std::string msg) {
    if (expected != actual) {
        throw msg;
    }
}

template<typename Func>
void assertException(Func func, const std::string msg) {
    bool got_exception = false;
    try {
        func();
    } catch (const std::exception& ex) {
        std::cout  << "expected exception: \"" << ex.what() << "\" OK" << std::endl;
        got_exception = true;
    }
    if (! got_exception) {
        throw msg;
    }
}

void test_Buffer_read() {
    cout << "running test_Buffer_read ..." << endl;
    fs::path filepath1;    
    fs::path filepath2;
    {
        fs::path tempdir = prepare_temp_dir();
        filepath1 = tempdir / fs::path("somefile1");
        {
            fs::ofstream fileout;
            fileout.open(filepath1);
            fileout << "0123456";
            fileout.close();
        }
        filepath2 = tempdir / fs::path("somefile2");
        {
            fs::ofstream fileout;
            fileout.open(filepath2);
            fileout << "ABC";
            fileout.close();
        }
    }
    
    int fd1 = ::open(filepath1.string().c_str(), 0);
    if (fd1 == -1) {
        throw throw_errno_failure("open error in test_Buffer_read()");
    }
    FileDesc fdesc1(fd1);
    int fd2 = ::open(filepath2.string().c_str(), 0);
    if (fd2 == -1) {
        throw throw_errno_failure("open error in test_Buffer_read()");
    }
    FileDesc fdesc2(fd2);
    
    Buffer buffer(5);
    buffer.read(fdesc1.fd());
    assertEquals(5, buffer.position(), "bad position");
    assertEquals(0, buffer.remaining(), "bad remaining");
    assertEquals('0', buffer[0], "bad [0]");
    assertEquals('1', buffer[1], "bad [1]");    
    assertEquals('2', buffer[2], "bad [1]");    
    assertEquals('3', buffer[3], "bad [1]");    
    assertEquals('4', buffer[4], "bad [1]");    
    assertException([&] () { buffer.read(fdesc1.fd()); }, "cannot read buffer full");
    buffer.clear();
    buffer.read(fdesc1.fd());
    assertEquals(2, buffer.position(), "bad position");
    assertEquals(3, buffer.remaining(), "bad remaining");
    assertEquals('5', buffer[0], "bad [0]");
    assertEquals('6', buffer[1], "bad [1]");    
    buffer.read(fdesc2.fd());
    assertEquals(5, buffer.position(), "bad position");
    assertEquals(0, buffer.remaining(), "bad remaining");
    assertEquals('5', buffer[0], "bad [0]");
    assertEquals('6', buffer[1], "bad [1]");    
    assertEquals('A', buffer[2], "bad [2]");
    assertEquals('B', buffer[3], "bad [3]");    
    assertEquals('C', buffer[4], "bad [4]");    
    
    cout << "running test_Buffer_read DONE" << endl;
}

int run_uts() {
    test_Buffer_read();
    return 0;
}


}


int mouclade_run(Args& args)
{
    auto pty = Pty::open(args);
    int master_fd = pty->master_fd();
    
    set_window_size(master_fd, args.winsize_.rows_, args.winsize_.cols_);

    FileDesc::set_nonblocking(0);
    FileDesc::set_nonblocking(1);
    FileDesc::set_nonblocking(master_fd);
    
    vector<t_pollfd> polls(3);
    polls[0].fd = 0;
    polls[1].fd = 1;
    polls[2].fd = master_fd;

    PolledPipe in_2_slave(polls[0], polls[2], 1024);
    PolledPipe slave_2_out(polls[2], polls[1], 1024);

    for (;;) {
        for(auto fd_it = polls.begin(); fd_it != polls.end(); ++fd_it) {
            fd_it->events = 0;
            fd_it->revents = 0;
        }
        in_2_slave.prepare_polling();
        slave_2_out.prepare_polling();
        
        int polled = poll(&polls[0], polls.size(), -1);
        if (polled == -1) {
            throw std::runtime_error("poll error");
        }
        
        in_2_slave.handle_io();
        slave_2_out.handle_io();
    }
}

enum Action {
    RUN,
    HELP,
    UT
};

Action parse_args(int argc, char* argv[], Args& args) {
    program_opts::options_description desc("Options");
    desc.add_options()
        ("help", "prints help")
        ("ut", "run unit tests")
        ("term", "TERM env value to use, defaults to xterm")
        ("cols", "initial window cols")
        ("rows", "initial window rows")
    ;
    
    program_opts::variables_map opts;
    program_opts::store(program_opts::parse_command_line(argc, argv, desc), opts);
    program_opts::notify(opts);

    if (opts.count("help")) {
        cout << desc << endl;
        return Action::HELP;
    }
    if (opts.count("ut")) {
        return Action::UT;
    }
    if (opts.count("term")) {
        args.termenv_ = opts["term"].as<string>();
    }
    if (opts.count("cols")) {
        args.winsize_.cols_ = opts["cols"].as<int>();
    }
    if (opts.count("rows")) {
        args.winsize_.rows_ = opts["rows"].as<int>();
    }
    return Action::RUN;
}


}}

using namespace rpulp::mouclade;

int main(int argc, char* argv[]) {
    try {
        Args args;
        Action action = parse_args(argc, argv, args);
        switch(action) {
            case Action::HELP:
                return -1;
            case Action::UT:
                return ut::run_uts();
            case Action::RUN:
                return mouclade_run(args);
            default:
                throw runtime_error("unexpected action");
        }
        if (parse_args(argc, argv, args)) {
            return mouclade_run(args);
        } else {
            return -2;
        }
    } catch (const exception& ex) {
        cerr << "fatal error: " << ex.what() << endl;
    } catch (const string& ex) {
        cerr << "fatal error: " << ex << endl;
    }
    return -1;
}
