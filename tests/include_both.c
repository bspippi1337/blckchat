/* Compile-time integration check: both public C11 APIs may be included together. */
#include "blckauthz.h"
#include "blckroom.h"
int main(void) {
    blck_authz_session auth={0};
    blck_room room={0};
    (void)auth;
    (void)room;
    return 0;
}
