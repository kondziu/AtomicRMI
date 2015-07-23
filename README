Atomic RMI
==========

Branches
--------

This repository has many branches. Most of them are for hacking new features,
but some are different (oder or unstable) variants of Atomic RMI. We list a
number of the branches that can be useful.

     Branch                 Description
    ---------------------------------------------------------------------------
     master                 The most recent stable variant currently this 
                            is currently supposed to be set to optsva.

     optsva                 OptSVA algorithm implementation that bases on SVA
                            but distinguishes between reads and writes and uses
                            buferring to effect a number of concurrency
                            optimizations. It also comes with a bunch of other
                            optimizations: constant number of spawned threads
                            (regardless of the number of running transactions),
                            log buffer, as light synchronization as we could
                            make it where it made a difference. We also changed
                            packages and parts of the API here.

     sva                    SVA algorithm implementation. Does not distinguish 
                            between read and writes at all, allows manual 
                            rollback and has in-place modifications.

     sva-read-opt           SVA algorithm with read-only optimization tacked 
                            on. If an object is read-only by a transaction it
                            is copied to a buffer and imediatelly released;
                            subsequent accesses are performed on the buffer. 
                            All other objects are treated like SVA, so have
                            in-place modifications. Even though not heavily, it
                            should be relatively stable.
    
